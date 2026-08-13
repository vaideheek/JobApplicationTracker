import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import ApplicationsList from './pages/ApplicationsList';
import AddApplication from './pages/AddApplication';
import EditApplication from './pages/EditApplication';
import ApplicationDetail from './pages/ApplicationDetail';
import EmailImport from './pages/EmailImport';
import BulkImport from './pages/BulkImport';
import Login from './pages/Login';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/applications" element={<ApplicationsList />} />
          <Route path="/applications/new" element={<AddApplication />} />
          <Route path="/applications/:id" element={<ApplicationDetail />} />
          <Route path="/applications/:id/edit" element={<EditApplication />} />
          <Route path="/email-import" element={<EmailImport />} />
          <Route path="/bulk-import" element={<BulkImport />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
