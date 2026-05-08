import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { ArrowLeft, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';

export const authInputClassName =
  'h-12 rounded-2xl border-border/70 bg-background px-4 text-sm shadow-sm transition-[border-color,box-shadow,background-color] duration-200 placeholder:text-muted-foreground/90 focus-visible:border-primary/45 focus-visible:ring-[3px] focus-visible:ring-primary/12 focus-visible:ring-offset-0';

export const authSelectClassName =
  'h-12 w-full appearance-none rounded-2xl border border-border/70 bg-background px-4 pr-12 text-sm shadow-sm transition-[border-color,box-shadow,background-color] duration-200 focus:border-primary/45 focus:outline-none focus:ring-[3px] focus:ring-primary/12';

export const authToggleButtonClassName =
  'absolute right-3 top-1/2 inline-flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full text-muted-foreground transition-colors duration-200 hover:bg-primary/5 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 focus-visible:ring-offset-2';

interface AuthFieldProps {
  children: ReactNode;
  className?: string;
  error?: string;
  hint?: ReactNode;
  htmlFor?: string;
  label: string;
  optionalLabel?: ReactNode;
}

export function AuthField({
  children,
  className,
  error,
  hint,
  htmlFor,
  label,
  optionalLabel,
}: AuthFieldProps) {
  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex items-center justify-between gap-3">
        <Label htmlFor={htmlFor} className="text-sm font-semibold text-foreground">
          {label}
        </Label>
        {optionalLabel ? (
          <span className="text-xs font-medium text-muted-foreground">{optionalLabel}</span>
        ) : null}
      </div>

      {children}

      {error ? (
        <p className="text-xs font-medium leading-5 text-destructive">{error}</p>
      ) : hint ? (
        <p className="text-xs leading-5 text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

interface AuthSideItem {
  description: string;
  icon: LucideIcon;
  title: string;
}

interface AuthPageShellProps {
  asideDescription: string;
  asideEyebrow: string;
  asideFootnote?: ReactNode;
  asideItems: AuthSideItem[];
  asideTitle: string;
  backHref?: string;
  backLabel?: string;
  children: ReactNode;
  contentClassName?: string;
  description: string;
  eyebrow: string;
  footer?: ReactNode;
  notice?: ReactNode;
  title: string;
}

export function AuthPageShell({
  asideDescription,
  asideEyebrow,
  asideFootnote,
  asideItems,
  asideTitle,
  backHref,
  backLabel,
  children,
  contentClassName,
  description,
  eyebrow,
  footer,
  notice,
  title,
}: AuthPageShellProps) {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[linear-gradient(180deg,hsl(var(--muted)_/_0.55),hsl(var(--background)))] text-foreground">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-80 bg-[radial-gradient(circle_at_top_left,hsl(var(--primary)_/_0.18),transparent_52%),radial-gradient(circle_at_top_right,hsl(var(--accent)_/_0.14),transparent_44%)]"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 bottom-0 h-56 bg-[linear-gradient(180deg,hsl(var(--background)_/_0),hsl(var(--muted)_/_0.55))]"
      />

      <div className="relative mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-6 px-4 py-4 sm:px-6 sm:py-6 lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(320px,0.95fr)] lg:gap-8 lg:px-8 lg:py-10">
        <section className="flex flex-col justify-center">
          <div className={cn('mx-auto w-full max-w-[34rem]', contentClassName)}>
            <div className="flex flex-wrap items-center gap-3">
              <Link
                to="/"
                className="inline-flex items-center gap-3 rounded-full border border-primary/10 bg-background/85 px-3.5 py-2.5 text-left shadow-soft transition-colors duration-200 hover:border-primary/20 hover:bg-background"
              >
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground shadow-sm">
                  P
                </div>
                <div className="min-w-0">
                  <p className="text-[0.7rem] font-semibold uppercase tracking-[0.18em] text-primary/75">
                    PrimeCare
                  </p>
                  <p className="truncate text-sm font-medium text-foreground">Cổng tài khoản</p>
                </div>
              </Link>

              <div className="inline-flex items-center gap-2 rounded-full border border-primary/10 bg-primary/5 px-3 py-1.5 text-xs font-medium text-primary/80">
                <ShieldCheck className="h-3.5 w-3.5" />
                Không gian đăng nhập rõ ràng, yên tâm
              </div>
            </div>

            <div className="mt-6 rounded-[2rem] border border-border/70 bg-card p-6 text-card-foreground shadow-card sm:p-8 lg:p-10">
              {backHref && backLabel ? (
                <Link
                  to={backHref}
                  className="mb-6 inline-flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors duration-200 hover:text-foreground"
                >
                  <ArrowLeft className="h-4 w-4" />
                  {backLabel}
                </Link>
              ) : null}

              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">{eyebrow}</p>
              <h1 className="mt-3 text-[2rem] font-semibold tracking-tight text-foreground sm:text-[2.25rem]">
                {title}
              </h1>
              <p className="mt-3 max-w-2xl text-sm leading-7 text-muted-foreground sm:text-[0.95rem]">
                {description}
              </p>

              {notice ? (
                <div className="mt-6 rounded-[1.5rem] border border-primary/10 bg-primary/5 px-4 py-4 text-sm leading-6 text-foreground/80">
                  {notice}
                </div>
              ) : null}

              <div className="mt-8">{children}</div>

              {footer ? <div className="mt-8 border-t border-border/70 pt-5">{footer}</div> : null}
            </div>
          </div>
        </section>

        <aside className="flex flex-col justify-center lg:py-6">
          <div className="relative overflow-hidden rounded-[2rem] border border-border/70 bg-[linear-gradient(180deg,hsl(var(--card)_/_0.98),hsl(var(--muted)_/_0.82))] p-6 text-card-foreground shadow-card sm:p-8 lg:sticky lg:top-10">
            <div
              aria-hidden
              className="pointer-events-none absolute inset-x-0 top-0 h-36 bg-[radial-gradient(circle_at_top,hsl(var(--primary)_/_0.18),transparent_65%)]"
            />

            <div className="relative">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary/80">
                {asideEyebrow}
              </p>
              <h2 className="mt-3 max-w-sm text-[1.8rem] font-semibold tracking-tight text-foreground sm:text-[2rem]">
                {asideTitle}
              </h2>
              <p className="mt-3 max-w-md text-sm leading-7 text-foreground/75">{asideDescription}</p>
            </div>

            <div className="relative mt-7 grid gap-3">
              {asideItems.map((item) => (
                <div
                  key={item.title}
                  className="rounded-[1.5rem] border border-border/70 bg-background/80 p-4 shadow-soft"
                >
                  <div className="flex items-start gap-3">
                    <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                      <item.icon className="h-5 w-5" />
                    </div>
                    <div className="space-y-1">
                      <p className="text-sm font-semibold text-foreground">{item.title}</p>
                      <p className="text-sm leading-6 text-muted-foreground">{item.description}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {asideFootnote ? (
              <div className="relative mt-6 rounded-[1.5rem] border border-primary/10 bg-background/75 px-5 py-4 text-sm leading-6 text-foreground/80">
                {asideFootnote}
              </div>
            ) : null}
          </div>
        </aside>
      </div>
    </div>
  );
}
