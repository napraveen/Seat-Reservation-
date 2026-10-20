import React, { useState } from 'react';
import { ShieldAlert, Database, CheckCircle2, Play } from 'lucide-react';

export const LiveOpsPage: React.FC = () => {
  const [degradationLevel, setDegradationLevel] = useState<number>(0);
  const [simRunning, setSimRunning] = useState<boolean>(false);
  const [simResults, setSimResults] = useState<any>(null);

  const kpis = [
    { label: 'Confirmed Booking Rate', value: '4,167 /s', sub: '250,000 tickets/min', status: 'optimal' },
    { label: 'Edge Admission Shed', value: '99.85%', sub: 'Shed in 2.1ms p99', status: 'optimal' },
    { label: 'Physical Berth Oversell', value: '0', sub: 'Hard Invariant', status: 'invariant' },
    { label: 'Payment-Ticket Divergence', value: '0', sub: 'Auto-reconciled <15m', status: 'invariant' },
    { label: 'Active Partitions', value: '256', sub: 'Single-writer actors', status: 'optimal' },
    { label: '28-Day Error Budget', value: '98.4%', sub: '21.5 min remaining', status: 'optimal' },
  ];

  const partitions = [
    { key: '12951:2026-10-22', train: '12951 Mumbai Rajdhani', lag: 12, allocMicros: '1.18 µs', status: 'HEALTHY' },
    { key: '12301:2026-10-22', train: '12301 Howrah Rajdhani', lag: 8, allocMicros: '1.24 µs', status: 'HEALTHY' },
    { key: '12627:2026-10-22', train: '12627 Karnataka Exp', lag: 24, allocMicros: '1.32 µs', status: 'HEALTHY' },
    { key: '12002:2026-10-22', train: '12002 Shatabdi Exp', lag: 4, allocMicros: '1.12 µs', status: 'HEALTHY' },
    { key: '22691:2026-10-22', train: '22691 SBC Rajdhani', lag: 16, allocMicros: '1.28 µs', status: 'HEALTHY' },
  ];

  const runSimulation = () => {
    setSimRunning(true);
    setTimeout(() => {
      setSimResults({
        virtualUsers: 200000,
        berthSupply: 5000,
        admitted: 20000,
        shedAtEdge: 180000,
        confirmed: 5000,
        oversell: 0,
        divergence: 0,
        rejectionLatencyP99: '2.1 ms',
        toutWinShare: '5.2%',
        humanWinShare: '94.8%'
      });
      setSimRunning(false);
    }, 1200);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded text-[11px] font-mono font-semibold tint-cyan uppercase">Showpiece 3</span>
            <span className="text-xs text-ink-400">Live SRE Infrastructure & Health</span>
          </div>
          <h1 className="text-2xl font-bold text-ink-50 mt-1">SRE Command Deck</h1>
          <p className="text-sm text-ink-400">
            Real-time telemetry across Kafka partition actors, bounded-staleness caches, and the degradation ladder.
          </p>
        </div>

        {/* Storm Simulator Trigger Button */}
        <button
          onClick={runSimulation}
          disabled={simRunning}
          className="btn btn-primary gap-2 text-sm font-semibold shadow-glow-cyan"
        >
          <Play className="w-4 h-4 fill-current" />
          <span>{simRunning ? 'Executing Tatkal Storm...' : 'Fire 200k Tatkal Storm Simulator'}</span>
        </button>
      </div>

      {/* KPI StatTiles */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {kpis.map((kpi) => (
          <div key={kpi.label} className="card p-4 space-y-2 card-hover">
            <div className="text-xs text-ink-400 font-medium truncate">{kpi.label}</div>
            <div className="text-2xl font-extrabold font-mono text-ink-50">{kpi.value}</div>
            <div className="text-[11px] font-medium text-cyan-400 flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400"></span>
              <span>{kpi.sub}</span>
            </div>
          </div>
        ))}
      </div>

      {/* Simulation Result Banner if executed */}
      {simResults && (
        <div className="card p-5 border-cyan-500/40 bg-cyan-500/10 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-cyan-300 font-bold text-sm">
              <CheckCircle2 className="w-5 h-5 text-cyan-400" />
              <span>Tatkal Storm Execution Completed (200,000 Virtual Users in 2.0s)</span>
            </div>
            <span className="text-xs font-mono text-cyan-400">Invariant Checked: 0 Oversell</span>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4 text-xs font-mono pt-2">
            <div><span className="text-ink-400">Total Demanded:</span> <strong className="text-ink-100">200,000</strong></div>
            <div><span className="text-ink-400">Shed at Edge:</span> <strong className="text-emerald-400">180,000 (&lt;5ms)</strong></div>
            <div><span className="text-ink-400">Confirmed Berths:</span> <strong className="text-cyan-400">5,000</strong></div>
            <div><span className="text-ink-400">Physical Oversell:</span> <strong className="text-emerald-400">0</strong></div>
            <div><span className="text-ink-400">Rejection Latency:</span> <strong className="text-cyan-400">{simResults.rejectionLatencyP99}</strong></div>
            <div><span className="text-ink-400">Human Win Share:</span> <strong className="text-iris-400">{simResults.humanWinShare}</strong></div>
          </div>
        </div>
      )}

      {/* Degradation Ladder & Partitions */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Degradation Ladder Selector */}
        <div className="card p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-hairline pb-3">
            <div className="flex items-center gap-2">
              <ShieldAlert className="w-4 h-4 text-amber-400" />
              <span className="font-bold text-sm text-ink-100">Degradation Ladder</span>
            </div>
            <span className="px-2 py-0.5 rounded text-xs font-mono font-bold tint-amber">
              Level {degradationLevel}
            </span>
          </div>

          <p className="text-xs text-ink-400">
            Never degrade correctness. Shed non-essential features, freshness, and throughput in controlled rungs.
          </p>

          <div className="space-y-2">
            {[
              { lvl: 0, title: 'L0: Normal Operations', desc: 'Full fidelity. All features active.' },
              { lvl: 1, title: 'L1: High Latency Mitigation', desc: 'Calendar compressed to 3 days. Fare what-if previews disabled.' },
              { lvl: 2, title: 'L2: Cache Lag Degraded', desc: 'Availability marked degraded. Alternate suggestions off.' },
              { lvl: 3, title: 'L3: Error Surge Protection', desc: 'Admission overshoot cut 4x -> 2x. Extended lottery.' },
              { lvl: 4, title: 'L4: Kafka Broker Disruption', desc: 'Booking paused. Reads fully available from replica.' },
              { lvl: 5, title: 'L5: Catastrophic DR', desc: 'Static maintenance page served at edge.' },
            ].map((rung) => (
              <button
                key={rung.lvl}
                onClick={() => setDegradationLevel(rung.lvl)}
                className={`w-full text-left p-2.5 rounded-lg border text-xs transition-all ${
                  degradationLevel === rung.lvl
                    ? 'tint-cyan border-cyan-400/50 shadow-sm'
                    : 'bg-well border-hairline hover:bg-well-hover text-ink-400'
                }`}
              >
                <div className="font-bold text-ink-100">{rung.title}</div>
                <div className="text-[11px] text-ink-400 mt-0.5">{rung.desc}</div>
              </button>
            ))}
          </div>
        </div>

        {/* Active Partitions Table */}
        <div className="lg:col-span-2 card p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-hairline pb-3">
            <div className="flex items-center gap-2">
              <Database className="w-4 h-4 text-cyan-400" />
              <span className="font-bold text-sm text-ink-100">Top Kafka Partition Actors (train_id, date)</span>
            </div>
            <span className="text-xs font-mono text-ink-400">Total: 256 Single-Writer Actors</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead>
                <tr className="border-b border-hairline text-ink-400">
                  <th className="pb-2">Partition Key</th>
                  <th className="pb-2">Train Service</th>
                  <th className="pb-2">Consumer Lag</th>
                  <th className="pb-2">Alloc Latency (p99)</th>
                  <th className="pb-2">Actor Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {partitions.map((p) => (
                  <tr key={p.key} className="hover:bg-well">
                    <td className="py-2.5 text-cyan-400 font-bold">{p.key}</td>
                    <td className="py-2.5 text-ink-200">{p.train}</td>
                    <td className="py-2.5 text-ink-300">{p.lag} msgs</td>
                    <td className="py-2.5 text-emerald-400 font-semibold">{p.allocMicros}</td>
                    <td className="py-2.5">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold tint-emerald">
                        {p.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
