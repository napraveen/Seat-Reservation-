import React, { useState, useEffect } from 'react';
import { Layers, Flame, Info, AlertCircle } from 'lucide-react';

interface BerthData {
  berthNumber: number;
  berthType: string;
  bayNumber: number;
  segmentMask: number;
  status: 'FREE' | 'PARTIAL' | 'FULL';
  occupiedLegs: string[];
  fragmentationWaste: number;
}

export const CoachMapPage: React.FC = () => {
  const [berths, setBerths] = useState<BerthData[]>([]);
  const [selectedBerth, setSelectedBerth] = useState<number | null>(null);
  const [hoveredBerth, setHoveredBerth] = useState<BerthData | null>(null);
  const [showFragmentationHeat, setShowFragmentationHeat] = useState<boolean>(false);
  const [selectedCoach, setSelectedCoach] = useState<string>('B3');

  const segments = ['NDLS -> KOTA', 'KOTA -> BRC', 'BRC -> ST', 'ST -> BCT'];

  useEffect(() => {
    // Generate deterministic 72-berth layout with realistic segment overlap
    const items: BerthData[] = [];
    const types = ['LB', 'MB', 'UB', 'LB', 'MB', 'UB', 'SL', 'SU'];

    for (let b = 1; b <= 72; b++) {
      const bay = Math.floor((b - 1) / 8) + 1;
      const type = types[(b - 1) % 8];

      let status: 'FREE' | 'PARTIAL' | 'FULL';
      let mask = 0;
      const occupied: string[] = [];

      // Create realistic pattern
      if (b % 5 === 0) {
        status = 'FULL';
        mask = 0b1111;
        occupied.push(...segments);
      } else if (b % 3 === 0) {
        status = 'PARTIAL';
        mask = 0b0011; // NDLS -> BRC
        occupied.push(segments[0], segments[1]);
      } else if (b % 7 === 0) {
        status = 'PARTIAL';
        mask = 0b1100; // BRC -> BCT
        occupied.push(segments[2], segments[3]);
      } else {
        status = 'FREE';
      }

      const waste = status === 'PARTIAL' ? 0.5 : (status === 'FREE' ? 0.0 : 0.0);

      items.push({
        berthNumber: b,
        berthType: type,
        bayNumber: bay,
        segmentMask: mask,
        status,
        occupiedLegs: occupied,
        fragmentationWaste: waste
      });
    }

    setBerths(items);
  }, [selectedCoach]);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded text-[11px] font-mono font-semibold tint-cyan uppercase">Showpiece 1</span>
            <span className="text-xs text-ink-400">Train 12951 · Mumbai Rajdhani</span>
          </div>
          <h1 className="text-2xl font-bold text-ink-50 mt-1">Interactive 72-Berth Coach Map</h1>
          <p className="text-sm text-ink-400">
            Berths are leased <strong>per segment</strong>, not per trip. Hover any berth to inspect its multi-leg Gantt occupancy.
          </p>
        </div>

        {/* Coach Selector & Heat Toggle */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1 p-1 bg-well border border-hairline rounded-xl">
            {['B1', 'B2', 'B3', 'B4', 'B5'].map((c) => (
              <button
                key={c}
                onClick={() => setSelectedCoach(c)}
                className={`px-3 py-1 text-xs font-semibold rounded-lg transition-colors ${
                  selectedCoach === c ? 'tint-cyan font-bold' : 'text-ink-400 hover:text-ink-100'
                }`}
              >
                {c}
              </button>
            ))}
          </div>

          <button
            onClick={() => setShowFragmentationHeat(!showFragmentationHeat)}
            className={`btn gap-2 text-xs font-medium border ${
              showFragmentationHeat ? 'tint-amber border-amber-500/50' : 'bg-well border-hairline text-ink-300'
            }`}
          >
            <Flame className="w-3.5 h-3.5 text-amber-400" />
            <span>Fragmentation Heat</span>
          </button>
        </div>
      </div>

      {/* Main Grid & Inspector Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* The 72-Berth Coach Layout */}
        <div className="lg:col-span-3 card p-6 space-y-6">
          <div className="flex items-center justify-between text-xs text-ink-400 border-b border-hairline pb-3">
            <div className="flex items-center gap-4">
              <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-emerald-500/30 border border-emerald-500"></span> Free Entire Route</span>
              <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-amber-500/30 border border-amber-500"></span> Partially Occupied (Intermediate Legs)</span>
              <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-red-500/30 border border-red-500"></span> Sold Out</span>
            </div>
            <span className="font-mono">Coach {selectedCoach} (3A · 72 Berths)</span>
          </div>

          {/* Bays of 8 representation */}
          <div className="grid grid-cols-1 sm:grid-cols-3 md:grid-cols-3 lg:grid-cols-3 gap-4">
            {Array.from({ length: 9 }).map((_, bayIdx) => {
              const bayNumber = bayIdx + 1;
              const bayBerths = berths.filter((b) => b.bayNumber === bayNumber);

              return (
                <div key={bayNumber} className="p-3.5 rounded-xl border border-hairline bg-base-850/40 space-y-2">
                  <div className="flex items-center justify-between text-[11px] font-mono text-ink-400">
                    <span>Bay {bayNumber}</span>
                    <span className="text-[10px] text-ink-500">Berths {(bayNumber - 1) * 8 + 1}-{bayNumber * 8}</span>
                  </div>

                  {/* Coupe Berths (6: Lower/Middle/Upper) + Side Berths (2: SL/SU) */}
                  <div className="grid grid-cols-4 gap-1.5">
                    {/* Coupe Berths */}
                    <div className="col-span-3 grid grid-cols-3 gap-1">
                      {bayBerths.slice(0, 6).map((berth) => renderBerthBox(berth))}
                    </div>
                    {/* Aisle & Side Berths */}
                    <div className="col-span-1 grid grid-cols-1 gap-1 border-l border-hairline pl-1.5">
                      {bayBerths.slice(6, 8).map((berth) => renderBerthBox(berth))}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Berth Segment Inspector & Gantt Chart */}
        <div className="lg:col-span-1 space-y-4">
          <div className="card p-5 space-y-4 sticky top-24">
            <div className="flex items-center gap-2 text-ink-100 font-semibold text-sm border-b border-hairline pb-3">
              <Info className="w-4 h-4 text-cyan-400" />
              <span>Interval Segment Inspector</span>
            </div>

            {hoveredBerth || selectedBerth ? (
              (() => {
                const b = hoveredBerth || berths.find((x) => x.berthNumber === selectedBerth)!;
                return (
                  <div className="space-y-4 text-sm">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="text-xl font-bold text-ink-50">Berth {b.berthNumber}</div>
                        <div className="text-xs text-ink-400">{b.berthType} · Bay {b.bayNumber} · Coach {selectedCoach}</div>
                      </div>
                      <span className={`px-2 py-1 rounded text-xs font-bold font-mono ${
                        b.status === 'FREE' ? 'tint-emerald' : b.status === 'PARTIAL' ? 'tint-amber' : 'tint-red'
                      }`}>
                        {b.status}
                      </span>
                    </div>

                    {/* Gantt Strip Across 4 Journey Segments */}
                    <div className="space-y-1.5">
                      <div className="text-xs font-semibold text-ink-300">Route Interval Occupancy Strip:</div>
                      <div className="space-y-1">
                        {segments.map((seg, idx) => {
                          const isOccupied = (b.segmentMask & (1 << idx)) !== 0;
                          return (
                            <div key={seg} className="flex items-center justify-between text-xs p-1.5 rounded bg-well border border-hairline">
                              <span className="font-mono text-ink-300">{seg}</span>
                              <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                isOccupied ? 'bg-red-500/20 text-red-400' : 'bg-emerald-500/20 text-emerald-400'
                              }`}>
                                {isOccupied ? 'BOOKED' : 'FREE'}
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    </div>

                    {b.status === 'PARTIAL' && (
                      <div className="p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/30 text-xs text-amber-300 space-y-1">
                        <div className="font-semibold flex items-center gap-1">
                          <AlertCircle className="w-3.5 h-3.5" />
                          <span>Interval Insight:</span>
                        </div>
                        <p>
                          This berth is booked between <strong>NDLS-BRC</strong>, but remains available for passengers traveling <strong>BRC-BCT</strong>!
                        </p>
                      </div>
                    )}
                  </div>
                );
              })()
            ) : (
              <div className="text-center py-12 text-ink-500 text-xs space-y-2">
                <Layers className="w-8 h-8 mx-auto text-ink-600" />
                <p>Hover over or click any berth in the coach grid to inspect its multi-segment occupancy.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );

  function renderBerthBox(berth: BerthData) {
    const isSelected = selectedBerth === berth.berthNumber;
    let bgClass = 'bg-emerald-500/15 border-emerald-500/30 text-emerald-300 hover:border-emerald-400';

    if (berth.status === 'FULL') {
      bgClass = 'bg-red-500/15 border-red-500/30 text-red-400 opacity-60';
    } else if (berth.status === 'PARTIAL') {
      bgClass = 'bg-amber-500/20 border-amber-500/40 text-amber-300 hover:border-amber-400';
    }

    if (showFragmentationHeat && berth.fragmentationWaste > 0) {
      bgClass = 'bg-orange-500/30 border-orange-500 text-orange-200 animate-pulse';
    }

    if (isSelected) {
      bgClass = 'bg-cyan-500/30 border-cyan-400 text-cyan-200 shadow-glow-cyan ring-1 ring-cyan-400';
    }

    return (
      <button
        key={berth.berthNumber}
        onClick={() => setSelectedBerth(berth.berthNumber)}
        onMouseEnter={() => setHoveredBerth(berth)}
        className={`p-1.5 rounded-md border text-center transition-all flex flex-col items-center justify-center ${bgClass}`}
      >
        <span className="text-[11px] font-mono font-bold">{berth.berthNumber}</span>
        <span className="text-[9px] opacity-80">{berth.berthType}</span>
      </button>
    );
  }
};
