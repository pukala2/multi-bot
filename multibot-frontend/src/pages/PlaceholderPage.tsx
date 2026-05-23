import { Topbar } from '@/components/layout/Topbar';
import { Card, CardBody } from '@/components/ui/card';

interface PlaceholderPageProps {
  title: string;
  phaseLabel: string;
  description: string;
}

export function PlaceholderPage({ title, phaseLabel, description }: PlaceholderPageProps) {
  return (
    <>
      <Topbar title={title} />
      <Card>
        <CardBody className="text-center py-16">
          <div className="inline-block px-3 py-1 rounded-md bg-bg-elevated text-text-muted text-[11px] uppercase tracking-wider font-semibold mb-3">
            {phaseLabel}
          </div>
          <p className="text-text-muted text-[14px] max-w-md mx-auto">{description}</p>
        </CardBody>
      </Card>
    </>
  );
}
