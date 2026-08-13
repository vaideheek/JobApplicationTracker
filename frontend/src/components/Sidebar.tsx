import { NavLink } from 'react-router-dom';
import { LayoutDashboard, List, PlusCircle, Briefcase, Mail, LogOut, User, FolderUp } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/applications', label: 'Applications', icon: List },
  { to: '/applications/new', label: 'Add New', icon: PlusCircle },
  { to: '/email-import', label: 'Email Import', icon: Mail },
  { to: '/bulk-import', label: 'Bulk Import', icon: FolderUp },
];

export default function Sidebar() {
  const { user, logout } = useAuth();

  return (
    <aside className="hidden w-64 flex-shrink-0 border-r border-slate-200 bg-white md:flex md:flex-col">
      {/* Logo */}
      <div className="flex h-16 items-center gap-3 border-b border-slate-200 px-6">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-white">
          <Briefcase size={20} />
        </div>
        <span className="text-lg font-semibold text-slate-900">JobTrack</span>
      </div>

      {/* Navigation */}
      <nav className="flex-1 space-y-1 px-3 py-4">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-brand-50 text-brand-700'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
              }`
            }
          >
            <item.icon size={20} />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Footer / Account section */}
      <div className="border-t border-slate-200 p-4 space-y-3 bg-slate-50/50">
        {user && (
          <div className="flex items-center gap-2.5 px-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-brand-700">
              <User size={16} />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-slate-700 truncate">
                {user.displayName || user.username}
              </p>
              <p className="text-[10px] text-slate-400">
                {user.demoAccount ? 'Demo Account' : 'Administrator'}
              </p>
            </div>
          </div>
        )}
        <button
          onClick={logout}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50 transition-colors"
        >
          <LogOut size={18} />
          <span>Log out</span>
        </button>
      </div>
    </aside>
  );
}
