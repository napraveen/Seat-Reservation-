import React, { useState, useEffect } from 'react';
import { Zap, ShieldCheck, Clock, ArrowRight, CheckCircle } from 'lucide-react';

export const TatkalStormPage: React.FC = () => {
  const [inQueue, setInQueue] = useState<boolean>(false);
  const [queuePosition, setQueuePosition] = useState<number>(142);
  const [etaSeconds, setEtaSeconds] = useState<number>(3.2);
  const [queueStatus, setQueueStatus] = useState<'WAITING' | 'PROCESSED' | 'REJECTED'>('WAITING');
  const [mode, setMode] = useState<'LOTTERY' | 'FCFS'>('LOTTERY');

  // Simulated queue countdown when active
  useEffect(() => {
    if (!inQueue || queueStatus !== 'WAITING') return;

    const interval = setInterval(() => {
      setQueuePosition((prev) => {
        const next = Math.max(0, prev - 24);
        if (next === 0) {
          setQueueStatus('PROCESSED');
        }
        return next;
      });
      setEtaSeconds((prev) => Math.max(0, Math.round((prev - 0.4) * 10) / 10));
    }, 400);

    return () => clearInterval(interval);
  }, [inQueue, queueStatus]);

  const startQueue = () => {
    setInQueue(true);
    setQueuePosition(168);
    setEtaSeconds(3.8);
    setQueueStatus('WAITING');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded text-[11px] font-mono font-semibold tint-cyan uppercase">Showpiece 2</span>
            <span className="text-xs text-ink-400">Mechanism Design vs. Latency Auction</span>
          </div>
          <h1 className="text-2xl font-bold text-ink-50 mt-1">Tatkal Virtual Queue & Anti-Tout Lottery</h1>
          <p className="text-sm text-ink-400">
            Compare pure FCFS (where 10ms fiber bots win) against RailFlow's 2,000ms verifiable fair lottery.
          </p>
        </div>

        {/* Mode Toggle */}
        <div className="flex items-center gap-2 bg-well border border-hairline p-1 rounded-xl">
          <button
            onClick={() => setMode('LOTTERY')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              mode === 'LOTTERY' ? 'tint-cyan font-bold shadow-sm' : 'text-ink-400 hover:text-ink-100'
            }`}
          >
            RailFlow Fair Lottery (VRF)
          </button>
          <button
            onClick={() => setMode('FCFS')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              mode === 'FCFS' ? 'tint-red font-bold shadow-sm' : 'text-ink-400 hover:text-ink-100'
            }`}
          >
            Classic FCFS (Today's Flaw)
          </button>
        </div>
      </div>

      {/* Live Queue Display Card */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 card p-6 space-y-6 relative overflow-hidden">
          <div className="flex items-center justify-between border-b border-hairline pb-4">
            <div className="flex items-center gap-2">
              <Zap className="w-5 h-5 text-cyan-400" />
              <span className="font-bold text-base text-ink-100">Tatkal Burst Window · 10:00:00 AM IST</span>
            </div>
            <span className="px-2.5 py-1 rounded-full text-xs font-mono font-semibold tint-emerald">
              Authoritative Clock Synced
            </span>
          </div>

          {!inQueue ? (
            <div className="py-12 text-center space-y-4">
              <div className="w-16 h-16 rounded-2xl tint-cyan mx-auto flex items-center justify-center shadow-glow-cyan">
                <Clock className="w-8 h-8 text-cyan-400" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-ink-50">Join the Simulated 10:00:00 Tatkal Rush</h3>
                <p className="text-sm text-ink-400 max-w-md mx-auto mt-1">
                  Experience how RailFlow evaluates admission in &lt;5ms and provides truthful queue ETA via Server-Sent Events.
                </p>
              </div>
              <button onClick={startQueue} className="btn btn-primary px-6 py-2.5 text-sm gap-2">
                <span>Submit Tatkal Booking Intent</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <div className="py-6 space-y-6">
              {/* Queue Stats Bar */}
              <div className="grid grid-cols-3 gap-4 text-center">
                <div className="p-4 rounded-xl bg-well border border-hairline">
                  <div className="text-xs text-ink-400 font-medium">Queue Position</div>
                  <div className="text-3xl font-extrabold font-mono text-cyan-400 mt-1">
                    #{queuePosition}
                  </div>
                </div>
                <div className="p-4 rounded-xl bg-well border border-hairline">
                  <div className="text-xs text-ink-400 font-medium">Estimated Wait Time</div>
                  <div className="text-3xl font-extrabold font-mono text-ink-50 mt-1">
                    {etaSeconds}s
                  </div>
                </div>
                <div className="p-4 rounded-xl bg-well border border-hairline">
                  <div className="text-xs text-ink-400 font-medium">Status</div>
                  <div className="text-xl font-bold font-mono mt-2">
                    {queueStatus === 'PROCESSED' ? (
                      <span className="text-emerald-400 flex items-center justify-center gap-1">
                        <CheckCircle className="w-5 h-5" /> Admitted!
                      </span>
                    ) : (
                      <span className="text-amber-400 flex items-center justify-center gap-1">
                        <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span> Queued
                      </span>
                    )}
                  </div>
                </div>
              </div>

              {/* Progress Bar */}
              <div className="space-y-2">
                <div className="flex justify-between text-xs text-ink-400 font-mono">
                  <span>Advancing through single-writer actor partition...</span>
                  <span>{168 - queuePosition} / 168 requests resolved</span>
                </div>
                <div className="w-full h-2.5 rounded-full bg-base-800 overflow-hidden border border-hairline">
                  <div
                    className="h-full bg-cyan-400 transition-all duration-300"
                    style={{ width: `${((168 - queuePosition) / 168) * 100}%` }}
                  ></div>
                </div>
              </div>

              {queueStatus === 'PROCESSED' && (
                <div className="p-4 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 text-sm flex items-center justify-between">
                  <div>
                    <div className="font-bold">Allocation Successful: Berth Held!</div>
                    <div className="text-xs text-emerald-400/80">Coach B3 · Berth 23 (Lower) · Hold active for 600 seconds.</div>
                  </div>
                  <button onClick={startQueue} className="btn btn-primary text-xs">
                    Test Again
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Side Panel: Fairness Evaluation Chart */}
        <div className="card p-6 space-y-5">
          <div className="flex items-center gap-2 border-b border-hairline pb-3">
            <ShieldCheck className="w-5 h-5 text-iris-400" />
            <span className="font-bold text-sm text-ink-100">Win Rate by Network Latency Cohort</span>
          </div>

          <p className="text-xs text-ink-400">
            {mode === 'LOTTERY'
              ? 'Under RailFlow’s VRF lottery, win rates are mathematically flat across all connection speeds.'
              : 'Under classic FCFS, low-latency script syndicates claim ~84% of all available supply.'}
          </p>

          {/* Cohort Bars */}
          <div className="space-y-3 pt-2">
            {[
              { cohort: '10ms (Tout Cloud Colocation)', rate: mode === 'LOTTERY' ? 25.0 : 78.4, color: mode === 'LOTTERY' ? 'bg-cyan-400' : 'bg-red-500' },
              { cohort: '50ms (Metro Fiber User)', rate: mode === 'LOTTERY' ? 25.0 : 14.8, color: mode === 'LOTTERY' ? 'bg-cyan-400' : 'bg-amber-400' },
              { cohort: '150ms (Normal 4G/5G Human)', rate: mode === 'LOTTERY' ? 25.0 : 5.2, color: mode === 'LOTTERY' ? 'bg-cyan-400' : 'bg-slate-500' },
              { cohort: '400ms (Rural Tier-3 Connection)', rate: mode === 'LOTTERY' ? 25.0 : 1.6, color: mode === 'LOTTERY' ? 'bg-cyan-400' : 'bg-slate-600' }
            ].map((item) => (
              <div key={item.cohort} className="space-y-1">
                <div className="flex justify-between text-xs text-ink-300">
                  <span>{item.cohort}</span>
                  <span className="font-mono font-bold">{item.rate}%</span>
                </div>
                <div className="w-full h-2 rounded-full bg-base-800 overflow-hidden border border-hairline">
                  <div
                    className={`h-full ${item.color} transition-all duration-500`}
                    style={{ width: `${item.rate}%` }}
                  ></div>
                </div>
              </div>
            ))}
          </div>

          <div className="p-3 rounded-lg bg-well border border-hairline text-[11px] text-ink-400">
            <strong>Skew Ratio ($p10 / p90$ RTT):</strong>{' '}
            <span className="font-mono font-bold text-ink-200">
              {mode === 'LOTTERY' ? '1.01x (Near-Perfect Parity)' : '5.3x Advantage for Touts'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
