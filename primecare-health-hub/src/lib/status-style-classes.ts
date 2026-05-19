export type StatusTone =
  | 'neutral'
  | 'primary'
  | 'accent'
  | 'info'
  | 'success'
  | 'warning'
  | 'destructive'
  | 'champagne';

export const statusToneClasses: Record<StatusTone, string> = {
  neutral: 'border-border bg-muted text-muted-foreground',
  primary: 'border-primary/20 bg-primary/10 text-primary',
  accent: 'border-accent/20 bg-accent/10 text-accent',
  info: 'border-info/20 bg-info/10 text-info',
  success: 'border-success/20 bg-success/10 text-success',
  warning: 'border-warning/20 bg-warning/10 text-warning',
  destructive: 'border-destructive/20 bg-destructive/10 text-destructive',
  champagne: 'border-champagne/20 bg-champagne/10 text-champagne',
};

export const statusDotClasses: Record<StatusTone, string> = {
  neutral: 'bg-muted-foreground',
  primary: 'bg-primary',
  accent: 'bg-accent',
  info: 'bg-info',
  success: 'bg-success',
  warning: 'bg-warning',
  destructive: 'bg-destructive',
  champagne: 'bg-champagne',
};

export const statusTextClasses: Record<StatusTone, string> = {
  neutral: 'text-muted-foreground',
  primary: 'text-primary',
  accent: 'text-accent',
  info: 'text-info',
  success: 'text-success',
  warning: 'text-warning',
  destructive: 'text-destructive',
  champagne: 'text-champagne',
};

export function getAccountBadgeClass(hasAccount?: boolean) {
  return hasAccount ? statusToneClasses.success : statusToneClasses.neutral;
}

export function getToggleStatusActionClass(isActive?: boolean) {
  return isActive ? statusTextClasses.warning : statusTextClasses.success;
}
