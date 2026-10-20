import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { Train, Sun, Moon, Zap, Search, Activity, LayoutGrid, Ticket } from 'lucide-react';

export const TopNav: React.FC = () => {
  const [isDark, setIsDark] = useState<boolean>(true);
  const [serverTime, setServerTime] = useState<string>('');

  useEffect(() => {
    const isDarkStored = localStorage.getItem('railflow-theme') !== 'light';
    setIsDark(isDarkStored);
    if (isDarkStored) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }

    const timer = setInterval(() => {
      const now = new Date();
      setServerTime(now.toLocaleTimeString('en-IN', { timeZone: 'Asia/Kolkata', hour12: false }) + ' IST');
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  const toggleTheme = () => {
    const next = !isDark;
    setIsDark(next);
    localStorage.setItem('railflow-theme', next ? 'dark' : 'light');
    if (next) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  };

  const navItems = [
    { to: '/', label: 'Book', icon: Ticket },
    { to: '/coach-map', label: 'Coach Map', icon: LayoutGrid },
    { to: '/pnr', label: 'PNR Status', icon: Search },
    { to: '/tatkal-storm', label: 'Tatkal Queue', icon: Zap },
    { to: '/live-ops', label: 'SRE Command Deck', icon: Activity },
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-hairline bg-base-900/80 backdrop-blur-xl transition-colors">
      <div className="max-w-[1600px] mx-auto px-4 lg:px-8 h-16 flex items-center justify-between gap-4">
        {/* Brand Block */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl tint-cyan flex items-center justify-center shadow-glow-cyan">
            <Train className="w-5 h-5 text-cyan-400" />
          </div>
          <div className="flex flex-col">
            <span className="text-xs font-bold uppercase tracking-wider text-cyan-400">RailFlow</span>
            <span className="text-sm font-semibold text-ink-100 hidden sm:inline">Reservation Control Plane</span>
          </div>
        </div>

        {/* Center Horizontal Nav */}
        <nav className="flex items-center gap-1 sm:gap-2">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `px-3 py-1.5 rounded-lg text-xs sm:text-sm font-medium flex items-center gap-2 transition-all ${
                  isActive
                    ? 'tint-cyan font-semibold shadow-sm'
                    : 'text-ink-400 hover:text-ink-100 hover:bg-well'
                }`
              }
            >
              <item.icon className="w-4 h-4" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Right Cluster */}
        <div className="flex items-center gap-3">
          {/* Server Clock */}
          <div className="hidden md:flex items-center gap-2 px-3 py-1 rounded-md bg-well border border-hairline text-xs font-mono text-ink-300">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span>{serverTime || '10:00:00 IST'}</span>
          </div>

          {/* Theme Toggle */}
          <button
            onClick={toggleTheme}
            className="w-9 h-9 rounded-lg border border-hairline bg-well hover:bg-well-hover flex items-center justify-center text-ink-300 hover:text-ink-100 transition-colors"
            title="Toggle Light/Dark Theme"
          >
            {isDark ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-iris-400" />}
          </button>
        </div>
      </div>
    </header>
  );
};
