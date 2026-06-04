import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import ApplicationsList from './pages/ApplicationsList';
import AddApplication from './pages/AddApplication';
import EditApplication from './pages/EditApplication';
import ApplicationDetail from './pages/ApplicationDetail';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/applications" element={<ApplicationsList />} />
        <Route path="/applications/new" element={<AddApplication />} />
        <Route path="/applications/:id" element={<ApplicationDetail />} />
        <Route path="/applications/:id/edit" element={<EditApplication />} />
      </Route>
    </Routes>
  );
}
