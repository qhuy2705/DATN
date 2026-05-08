import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="mb-ui-lg overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <div className="flex flex-col gap-ui-md px-ui-lg py-ui-lg lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-ui-xs">
          <div className="inline-flex items-center rounded-full border border-primary/15 bg-primary/5 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-primary">
            PrimeCare
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-[1.9rem]">
              {title}
            </h1>
            {description && (
              <p className="mt-ui-xs max-w-3xl text-sm leading-6 text-muted-foreground sm:text-[15px]">
                {description}
              </p>
            )}
          </div>
        </div>
        {actions && <div className="flex flex-wrap gap-ui-xs lg:justify-end">{actions}</div>}
      </div>
    </div>
  );
}
