import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { TopNav } from './components/TopNav';
import { BookingPage } from './pages/BookingPage';
import { CoachMapPage } from './pages/CoachMapPage';
import { TatkalStormPage } from './pages/TatkalStormPage';
import { LiveOpsPage } from './pages/LiveOpsPage';
import { PnrStatusPage } from './pages/PnrStatusPage';

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <div className="min-h-screen flex flex-col">
        <TopNav />
        <main className="flex-1 max-w-[1600px] w-full mx-auto px-4 lg:px-8 py-6">
          <Routes>
            <Route path="/" element={<BookingPage />} />
            <Route path="/coach-map" element={<CoachMapPage />} />
            <Route path="/tatkal-storm" element={<TatkalStormPage />} />
            <Route path="/live-ops" element={<LiveOpsPage />} />
            <Route path="/pnr" element={<PnrStatusPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
};
export default App;
