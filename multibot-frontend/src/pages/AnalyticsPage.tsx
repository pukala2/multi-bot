import { useState } from 'react';
import { Topbar } from '@/components/layout/Topbar';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { BarChart, type BarChartPoint } from '@/components/domain/BarChart';
import { Heatmap } from '@/components/domain/Heatmap';
import { useAnalytics, type AnalyticsPeriod } from '@/hooks/useAnalytics';
import { shortUuid, timeAgo } from '@/lib/format';
import { cn } from '@/lib/cn';

const PERIODS: { value: AnalyticsPeriod; label: string }[] = [
  { value: 'D1', label: '24h' },
  { value: 'D7', label: '7 dni' },
  { value: 'D30', label: '30 dni' },
  { value: 'D90', label: '90 dni' },
];

function formatPercent(value: number): string {
  const sign = value > 0 ? '↑' : value < 0 ? '↓' : '';
  return `${sign} ${Math.abs(value).toFixed(0)}%`;
}

function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${String(secs).padStart(2, '0')}`;
}

function deltaClass(value: number): string {
  return value > 0 ? 'text-success' : value < 0 ? 'text-error' : 'text-text-dim';
}

const DAY_NAMES = ['', 'poniedziałek', 'wtorek', 'środa', 'czwartek', 'piątek', 'sobota', 'niedziela'];

export function AnalyticsPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>('D7');
  const analytics = useAnalytics(period);
  const data = analytics.data;

  const dailySessions: BarChartPoint[] = (data?.dailyTimeseries ?? []).map((d) => {
    const date = new Date(d.date);
    const dayOfWeek = date.getDay();
    const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
    return {
      label: String(date.getDate()),
      value: d.sessions,
      tooltip: `${d.date}: ${d.sessions} sesji`,
      dim: isWeekend,
    };
  });

  const dailyMessages: BarChartPoint[] = (data?.dailyTimeseries ?? []).map((d) => {
    const date = new Date(d.date);
    const isWeekend = date.getDay() === 0 || date.getDay() === 6;
    return {
      label: String(date.getDate()),
      value: d.messages,
      tooltip: `${d.date}: ${d.messages.toLocaleString('pl-PL')} wiadomości`,
      dim: isWeekend,
    };
  });

  const peakHeatmapCell = (data?.hourlyHeatmap ?? []).reduce(
    (max, c) => (c.count > (max?.count ?? 0) ? c : max),
    null as null | typeof data extends { hourlyHeatmap: infer R } ? R extends Array<infer C> ? C : never : never
  );

  return (
    <>
      <Topbar
        title="Analityka"
        actions={
          <div className="inline-flex bg-bg-panel border border-border rounded-lg overflow-hidden">
            {PERIODS.map((p) => (
              <button
                key={p.value}
                onClick={() => setPeriod(p.value)}
                className={cn(
                  'px-3.5 py-1.5 text-[13px] transition-colors',
                  period === p.value
                    ? 'bg-accent/15 text-accent'
                    : 'text-text-muted hover:bg-bg-elevated hover:text-text'
                )}
              >
                {p.label}
              </button>
            ))}
          </div>
        }
      />

      {analytics.isPending ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Card key={i}><CardBody><div className="h-40 bg-bg-elevated/40 rounded animate-pulse" /></CardBody></Card>
          ))}
        </div>
      ) : !data ? null : (
        <>
          <div className="grid grid-cols-3 gap-4 mb-4">
            <Card>
              <CardBody className="space-y-2">
                <div className="text-xs text-text-muted">Sesje</div>
                <div className="text-[26px] font-semibold leading-none">{data.overview.sessions}</div>
                <div className="text-[11.5px]">
                  <span className={deltaClass(data.overview.sessionsDeltaPercent)}>
                    {formatPercent(data.overview.sessionsDeltaPercent)}
                  </span>
                  <span className="text-text-dim ml-1">vs poprzedni okres</span>
                </div>
              </CardBody>
            </Card>
            <Card>
              <CardBody className="space-y-2">
                <div className="text-xs text-text-muted">Wiadomości</div>
                <div className="text-[26px] font-semibold leading-none">{data.overview.messages}</div>
                <div className="text-[11.5px]">
                  <span className={deltaClass(data.overview.messagesDeltaPercent)}>
                    {formatPercent(data.overview.messagesDeltaPercent)}
                  </span>
                  <span className="text-text-dim ml-1">vs poprzedni okres</span>
                </div>
              </CardBody>
            </Card>
            <Card>
              <CardBody className="space-y-2">
                <div className="text-xs text-text-muted">Unikalni użytkownicy</div>
                <div className="text-[26px] font-semibold leading-none">{data.overview.uniqueUsers}</div>
                <div className="text-[11.5px]">
                  <span className={deltaClass(data.overview.uniqueUsersDeltaPercent)}>
                    {formatPercent(data.overview.uniqueUsersDeltaPercent)}
                  </span>
                  <span className="text-text-dim ml-1">{data.overview.returningUsersPercent.toFixed(0)}% powracający</span>
                </div>
              </CardBody>
            </Card>
          </div>

          <Card className="mb-4">
            <CardHeader>
              <div>
                <CardTitle>Zaangażowanie</CardTitle>
                <CardSubtitle>jak długo i jak głęboko klienci rozmawiają z botem</CardSubtitle>
              </div>
            </CardHeader>
            <CardBody>
              <div className="grid grid-cols-3 gap-3">
                <div className="bg-bg-elevated rounded-md p-4">
                  <div className="text-[11px] text-text-muted uppercase tracking-wider">Średnio wiad./sesja</div>
                  <div className="text-[22px] font-semibold mt-1.5">
                    {data.engagement.avgMessagesPerSession.toFixed(1)}
                  </div>
                  <div className="text-[11.5px] text-text-dim mt-0.5">
                    poprzedni okres: {data.engagement.previousAvgMessagesPerSession.toFixed(1)}
                  </div>
                </div>
                <div className="bg-bg-elevated rounded-md p-4">
                  <div className="text-[11px] text-text-muted uppercase tracking-wider">Średni czas sesji</div>
                  <div className="text-[22px] font-semibold mt-1.5">
                    {formatDuration(data.engagement.avgSessionDurationSeconds)}
                  </div>
                  <div className="text-[11.5px] text-text-dim mt-0.5">
                    od pierwszej do ostatniej wiadomości
                  </div>
                </div>
                <div className="bg-bg-elevated rounded-md p-4">
                  <div className="text-[11px] text-text-muted uppercase tracking-wider">Wiadomości łącznie</div>
                  <div className="text-[22px] font-semibold mt-1.5">
                    {data.overview.messages.toLocaleString('pl-PL')}
                  </div>
                  <div className="text-[11.5px] text-text-dim mt-0.5">
                    w {data.overview.sessions} sesjach
                  </div>
                </div>
              </div>
            </CardBody>
          </Card>

          <div className="grid grid-cols-2 gap-4 mb-4">
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Sesje dziennie</CardTitle>
                  <CardSubtitle>weekendy wyróżnione</CardSubtitle>
                </div>
              </CardHeader>
              <CardBody>
                <BarChart data={dailySessions} color="accent" />
              </CardBody>
            </Card>
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Wiadomości dziennie</CardTitle>
                  <CardSubtitle>razem: {data.overview.messages.toLocaleString('pl-PL')}</CardSubtitle>
                </div>
              </CardHeader>
              <CardBody>
                <BarChart data={dailyMessages} color="info" />
              </CardBody>
            </Card>
          </div>

          <Card className="mb-4">
            <CardHeader>
              <div>
                <CardTitle>Aktywność: godzina × dzień tygodnia</CardTitle>
                <CardSubtitle>
                  {peakHeatmapCell
                    ? `peak: ${DAY_NAMES[peakHeatmapCell.dayOfWeek]} ${peakHeatmapCell.hour}:00 — ${peakHeatmapCell.count} wiad.`
                    : 'brak danych'}
                </CardSubtitle>
              </div>
            </CardHeader>
            <CardBody>
              <Heatmap cells={data.hourlyHeatmap} />
            </CardBody>
          </Card>

          <div className="grid grid-cols-[2fr_1fr] gap-4 mb-4">
            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Najczęstsze pytania</CardTitle>
                  <CardSubtitle>top {data.topQuestions.length} — w wybranym okresie</CardSubtitle>
                </div>
              </CardHeader>
              {data.topQuestions.length === 0 ? (
                <CardBody className="py-10 text-center text-text-muted text-[13px]">
                  Brak danych dla wybranego okresu
                </CardBody>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead></TableHead>
                      <TableHead>Pytanie</TableHead>
                      <TableHead className="text-right">Wystąpienia</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.topQuestions.map((q, i) => (
                      <TableRow key={i}>
                        <TableCell className="text-text-dim text-[11px]">#{i + 1}</TableCell>
                        <TableCell className="max-w-[400px] truncate" title={q.content}>{q.content}</TableCell>
                        <TableCell className="text-right font-medium">{q.occurrences}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Card>

            <Card>
              <CardHeader>
                <div>
                  <CardTitle>Aktywność wersji</CardTitle>
                  <CardSubtitle>sesje obsłużone przez każdą wersję</CardSubtitle>
                </div>
              </CardHeader>
              <CardBody className="space-y-2">
                {data.versionActivity.map((v) => {
                  const max = Math.max(...data.versionActivity.map((x) => x.sessionCount), 1);
                  const percent = (v.sessionCount / max) * 100;
                  return (
                    <div key={v.versionId} className="space-y-1.5">
                      <div className="flex justify-between items-center">
                        <div>
                          <div className="text-[13px] font-medium">{v.name}</div>
                          <div className="text-[11px] text-text-muted">
                            {v.status === 'ACTIVE' && <span className="text-success">● aktywna</span>}
                            {v.status === 'DRAFT' && <span className="text-warning">● robocza</span>}
                            {v.status === 'ARCHIVED' && <span className="text-text-dim">● zarchiwizowana</span>}
                          </div>
                        </div>
                        <div className="text-[14px] font-semibold">
                          {v.sessionCount} <span className="text-text-dim text-[11px] font-normal">sesji</span>
                        </div>
                      </div>
                      <div className="h-1.5 bg-bg-elevated rounded-full overflow-hidden">
                        <div
                          className={cn('h-full rounded-full',
                            v.status === 'ACTIVE' ? 'bg-accent' : 'bg-text-dim/50')}
                          style={{ width: `${percent}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </CardBody>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Najdłuższe sesje</CardTitle>
                <CardSubtitle>top 5 — kandydaci na case study lub wsparcie 1:1</CardSubtitle>
              </div>
            </CardHeader>
            {data.longestSessions.length === 0 ? (
              <CardBody className="py-10 text-center text-text-muted text-[13px]">
                Brak długich sesji w wybranym okresie
              </CardBody>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead></TableHead>
                    <TableHead>Anonimowy ID</TableHead>
                    <TableHead>Wiadomości</TableHead>
                    <TableHead>Czas trwania</TableHead>
                    <TableHead>Ostatnia aktywność</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.longestSessions.map((s, i) => (
                    <TableRow key={s.sessionId}>
                      <TableCell className="text-text-dim text-[11px]">#{i + 1}</TableCell>
                      <TableCell className="mono text-text-muted">{shortUuid(s.anonymousId, 12, 4)}</TableCell>
                      <TableCell>{s.messageCount}</TableCell>
                      <TableCell>{formatDuration(s.durationSeconds)}</TableCell>
                      <TableCell className="text-text-muted">{timeAgo(s.lastActivityAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </>
      )}
    </>
  );
}
