import { LucideIcon } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  icon: LucideIcon;
  trend?: string;
  trendUp?: boolean;
  description?: string;
  className?: string;
}

export function StatCard({ title, value, icon: Icon, trend, trendUp, description, className = '' }: StatCardProps) {
  return (
    <div className={`rounded-lg border border-border bg-card p-ui-lg shadow-sm transition-shadow duration-200 hover:shadow-md ${className}`}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-medium text-muted-foreground">{title}</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-foreground">{value}</p>
          {description && (
            <p className="mt-2 text-xs leading-5 text-muted-foreground">
              {description}
            </p>
          )}
          {trend && (
            <p className={`text-xs mt-2 font-medium ${trendUp ? 'text-success' : 'text-destructive'}`}>
              {trendUp ? '↑' : '↓'} {trend}
            </p>
          )}
        </div>
        <div className="rounded-lg border border-primary/10 bg-primary/10 p-ui-sm shadow-sm">
          <Icon className="h-5 w-5 text-primary" />
        </div>
      </div>
    </div>
  );
}
