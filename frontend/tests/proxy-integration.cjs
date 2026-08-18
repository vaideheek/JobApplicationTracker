const http = require('http');
const { spawn } = require('child_process');
const assert = require('assert');
const path = require('path');

const frontendDir = path.resolve(__dirname, '..');
const mockBackendPort = 9090;
const wranglerPort = 8788;

let receivedRequests = [];

// Start a local HTTP server representing the backend on port 9090
const mockBackend = http.createServer((req, res) => {
  let chunks = [];
  req.on('data', chunk => { chunks.push(chunk); });
  req.on('end', () => {
    const bodyBuffer = Buffer.concat(chunks);
    receivedRequests.push({
      url: req.url,
      method: req.method,
      headers: req.headers,
      body: bodyBuffer.toString('utf8'),
      rawBody: bodyBuffer
    });

    // Route matching
    if (req.url.startsWith('/api/test-get')) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'ok', query: req.url }));
    } else if (req.url === '/api/test-post') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'received', body: bodyBuffer.toString('utf8') }));
    } else if (req.url === '/api/test-upload') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'uploaded', bodyLength: bodyBuffer.length }));
    } else if (req.url === '/api/test-download') {
      res.setHeader('Content-Type', 'application/octet-stream');
      res.setHeader('Content-Disposition', 'attachment; filename="data.bin"');
      res.appendHeader('Set-Cookie', 'JSESSIONID=session123; Path=/api; Secure; HttpOnly');
      res.appendHeader('Set-Cookie', 'OtherCookie=value; Path=/');
      res.writeHead(200);
      res.end(Buffer.from([0x01, 0x02, 0x7F, 0x80, 0xFF])); // includes values > 127 (128, 255)
    } else if (req.url === '/api/test-404') {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Not Found' }));
    } else {
      res.writeHead(500);
      res.end('Unknown mock route');
    }
  });
});

mockBackend.listen(mockBackendPort, () => {
  console.log(`Mock backend listening on port ${mockBackendPort}`);
  startWrangler();
});

let wranglerProcess = null;
let testsStarted = false;

function startWrangler() {
  console.log('Starting Wrangler Pages Dev Server...');

  wranglerProcess = spawn('npx', [
    'wrangler',
    'pages',
    'dev',
    'dist',
    '--port',
    wranglerPort.toString(),
    '--binding',
    `BACKEND_ORIGIN=http://localhost:${mockBackendPort}`,
    '--compatibility-date=2024-01-01'
  ], {
    cwd: frontendDir,
    shell: true
  });

  wranglerProcess.stdout.on('data', (data) => {
    const output = data.toString();
    console.log(`[Wrangler] ${output.trim()}`);
    if (output.includes('Ready') || output.includes('localhost:') || output.includes('8788')) {
      if (!testsStarted) {
        testsStarted = true;
        setTimeout(runTests, 2000);
      }
    }
  });

  wranglerProcess.stderr.on('data', (data) => {
    console.error(`[Wrangler Error] ${data.toString().trim()}`);
  });

  wranglerProcess.on('close', (code) => {
    console.log(`Wrangler process exited with code ${code}`);
  });
}

function makeRequest(options, requestBody = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      const chunks = [];
      res.on('data', chunk => { chunks.push(chunk); });
      res.on('end', () => {
        const responseBuffer = Buffer.concat(chunks);
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: responseBuffer.toString('utf8'),
          rawBody: responseBuffer
        });
      });
    });
    req.on('error', reject);
    if (requestBody) {
      req.write(requestBody);
    }
    req.end();
  });
}

// Sub-test for invalid configuration
function testInvalidOriginConfig(originStr, testName) {
  return new Promise((resolve, reject) => {
    console.log(`- ${testName}`);
    const invalidProcess = spawn('npx', [
      'wrangler',
      'pages',
      'dev',
      'dist',
      '--port',
      '8789',
      '--binding',
      `BACKEND_ORIGIN=${originStr}`,
      '--compatibility-date=2024-01-01'
    ], {
      cwd: frontendDir,
      shell: true
    });

    let started = false;

    invalidProcess.stdout.on('data', async (data) => {
      const output = data.toString();
      if (output.includes('Ready') || output.includes('localhost:') || output.includes('8789')) {
        if (!started) {
          started = true;
          try {
            const res = await makeRequest({
              hostname: 'localhost',
              port: 8789,
              path: '/api/test-get',
              method: 'GET'
            });
            assert.strictEqual(res.status, 500);
            const body = JSON.parse(res.body);
            assert.strictEqual(body.error, 'Configuration Error');
            assert.strictEqual(body.message, 'Invalid BACKEND_ORIGIN configuration');
            invalidProcess.kill('SIGINT');
            resolve();
          } catch (err) {
            invalidProcess.kill('SIGINT');
            reject(err);
          }
        }
      }
    });

    invalidProcess.on('error', (err) => {
      reject(err);
    });
  });
}

async function runTests() {
  console.log('\n--- Running Integration Tests ---');
  try {
    // 1. GET request forwarding (query strings, Cookie, CSRF)
    {
      console.log('- Test 1: GET query string, Cookie, and CSRF forwarding');
      const res = await makeRequest({
        hostname: 'localhost',
        port: wranglerPort,
        path: '/api/test-get?foo=bar&baz=qux',
        method: 'GET',
        headers: {
          'Cookie': 'JSESSIONID=cookie123',
          'X-CSRF-TOKEN': 'csrf123',
          'Accept': 'application/json'
        }
      });

      assert.strictEqual(res.status, 200);
      const backendReq = receivedRequests.find(r => r.url.startsWith('/api/test-get'));
      assert(backendReq, 'Mock backend did not receive request');
      assert.strictEqual(backendReq.headers['cookie'], 'JSESSIONID=cookie123');
      assert.strictEqual(backendReq.headers['x-csrf-token'], 'csrf123');
      assert.strictEqual(backendReq.headers['host'], `localhost:${mockBackendPort}`);
    }

    // 2. POST JSON body forwarding
    {
      console.log('- Test 2: POST JSON body forwarding');
      const postData = JSON.stringify({ key: 'value', num: 42 });
      const res = await makeRequest({
        hostname: 'localhost',
        port: wranglerPort,
        path: '/api/test-post',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(postData)
        }
      }, postData);

      assert.strictEqual(res.status, 200);
      const backendReq = receivedRequests.find(r => r.url === '/api/test-post');
      assert(backendReq, 'Mock backend did not receive POST request');
      assert.strictEqual(backendReq.headers['content-type'], 'application/json');
      assert.deepStrictEqual(JSON.parse(backendReq.body), { key: 'value', num: 42 });
    }

    // 3. Set-Cookie response forwarding
    {
      console.log('- Test 3: Set-Cookie response forwarding');
      const res = await makeRequest({
        hostname: 'localhost',
        port: wranglerPort,
        path: '/api/test-download',
        method: 'GET'
      });

      assert.strictEqual(res.status, 200);
      const setCookies = res.headers['set-cookie'];
      assert(setCookies, 'Set-Cookie header missing in proxy response');
      assert.strictEqual(setCookies.length, 2);
      assert(setCookies.some(c => c.includes('JSESSIONID=session123')));
      assert(setCookies.some(c => c.includes('OtherCookie=value')));
    }

    // 4. Multipart POST forwarding
    {
      console.log('- Test 4: Multipart Form Data forwarding');
      const boundary = '----WebKitFormBoundaryXYZ';
      const multipartBody = `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="test.txt"\r\nContent-Type: text/plain\r\n\r\nHello World\r\n--${boundary}--`;
      const res = await makeRequest({
        hostname: 'localhost',
        port: wranglerPort,
        path: '/api/test-upload',
        method: 'POST',
        headers: {
          'Content-Type': `multipart/form-data; boundary=${boundary}`,
          'Content-Length': Buffer.byteLength(multipartBody)
        }
      }, multipartBody);

      assert.strictEqual(res.status, 200);
      const backendReq = receivedRequests.find(r => r.url === '/api/test-upload');
      assert(backendReq, 'Mock backend did not receive upload request');
      assert(backendReq.body.includes('Hello World'));
    }

    // 5. Binary response forwarding (exact bytes comparison)
    {
      console.log('- Test 5: Binary response forwarding (preserves values > 127)');
      const res = await makeRequest({
        hostname: 'localhost',
        port: wranglerPort,
        path: '/api/test-download',
        method: 'GET'
      });

      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.headers['content-type'], 'application/octet-stream');
      assert.strictEqual(res.headers['content-disposition'], 'attachment; filename="data.bin"');

      const expectedBytes = [0x01, 0x02, 0x7F, 0x80, 0xFF];
      assert.deepStrictEqual([...res.rawBody], expectedBytes);
    }

    // 6. Backend 4xx response forwarding
    {
      console.log('- Test 6: Backend 4xx status propagation');
      const res = await makeRequest({
        hostname: 'localhost',
        port: wranglerPort,
        path: '/api/test-404',
        method: 'GET'
      });

      assert.strictEqual(res.status, 404);
      const body = JSON.parse(res.body);
      assert.strictEqual(body.error, 'Not Found');
    }

    // 7. Malformed protocol test (ftp:// protocol)
    await testInvalidOriginConfig('ftp://localhost', 'Test 7: Malformed BACKEND_ORIGIN protocol rejected');

    // 8. Malformed path suffix test (should reject extra path names)
    await testInvalidOriginConfig('http://localhost:9090/extra-path', 'Test 8: Malformed BACKEND_ORIGIN path rejected');

    console.log('\nAll integration tests passed successfully!');
    cleanup(0);
  } catch (err) {
    console.error('\nIntegration tests failed:', err);
    cleanup(1);
  }
}

function cleanup(exitCode) {
  console.log('Cleaning up processes...');
  if (wranglerProcess) {
    wranglerProcess.kill('SIGINT');
  }
  mockBackend.close(() => {
    console.log('Mock backend stopped.');
    process.exit(exitCode);
  });
}

// Timeout safety fallback
setTimeout(() => {
  if (!testsStarted) {
    console.error('Timeout waiting for Wrangler dev server to start');
    cleanup(1);
  }
}, 35000);
