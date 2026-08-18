function validateOrigin(originStr: string): URL | null {
  try {
    const url = new URL(originStr);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return null;
    }
    if (!url.hostname) {
      return null;
    }
    if (url.username || url.password) {
      return null;
    }
    if (url.search || url.hash) {
      return null;
    }
    if (url.pathname !== "/" && url.pathname !== "") {
      return null;
    }
    return url;
  } catch {
    return null;
  }
}

export const onRequest = async (context: any): Promise<Response> => {
  const { request, env } = context;

  const backendOrigin = env.BACKEND_ORIGIN;
  if (!backendOrigin) {
    return new Response(
      JSON.stringify({ error: "Backend Origin misconfigured", message: "BACKEND_ORIGIN binding is missing" }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }

  const validatedOrigin = validateOrigin(backendOrigin);
  if (!validatedOrigin) {
    return new Response(
      JSON.stringify({ error: "Configuration Error", message: "Invalid BACKEND_ORIGIN configuration" }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }

  // Parse incoming URL and map to target origin
  const incomingUrl = new URL(request.url);
  const targetUrl = new URL(incomingUrl.pathname + incomingUrl.search, validatedOrigin.origin);

  // Copy request headers safely, stripping the incoming Host header
  const newHeaders = new Headers(request.headers);
  newHeaders.delete("Host");

  const method = request.method.toUpperCase();
  const hasBody = method !== "GET" && method !== "HEAD";

  // Create a new request to the backend with standard Fetch API params
  const backendRequest = new Request(targetUrl.toString(), {
    method: request.method,
    headers: newHeaders,
    body: hasBody ? request.body : null,
    redirect: "manual"
  });

  try {
    const response = await fetch(backendRequest);

    // Copy all response headers to return to browser
    const responseHeaders = new Headers(response.headers);

    // Return the response with status, headers, and body stream
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders
    });
  } catch (error) {
    return new Response(
      JSON.stringify({ error: "Bad Gateway", message: "Backend service unavailable" }),
      { status: 502, headers: { "Content-Type": "application/json" } }
    );
  }
};
