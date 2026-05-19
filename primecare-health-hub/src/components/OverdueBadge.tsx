export function NoShowEligibleBadge() {
  return (
    <span className="inline-flex items-center whitespace-nowrap rounded-full border border-warning/25 bg-warning/10 px-2.5 py-1 text-xs font-medium leading-none text-warning">
      Đủ điều kiện no-show
    </span>
  );
}

export function LateCheckInBadge() {
  return (
    <span className="inline-flex items-center whitespace-nowrap rounded-full border border-warning/20 bg-warning/5 px-2.5 py-1 text-xs font-medium leading-none text-warning">
      Trễ check-in
    </span>
  );
}

export function CheckedInLateBadge({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center whitespace-nowrap rounded-full border border-border bg-muted/40 px-2.5 py-1 text-xs font-medium leading-none text-muted-foreground">
      {label}
    </span>
  );
}

export function OverdueBadge() {
  return <NoShowEligibleBadge />;
}
