import { Link, NavLink } from 'react-router-dom';
import {
  Activity,
  CalendarCheck2,
  ChevronDown,
  FileSearch,
  LogOut,
  Menu,
  Moon,
  Receipt,
  Shield,
  Sun,
  UserRound,
  X,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { Button } from '@/components/ui/button';
import { useThemeStore } from '@/stores/theme-store';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from '@/components/LanguageSwitcher';
import { useAuthStore } from '@/stores/auth-store';
import { useLogout } from '@/hooks/use-auth';
import { UserAvatar } from '@/components/UserAvatar';
import { cn } from '@/lib/utils';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

export function PublicHeader() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { theme, toggleTheme } = useThemeStore();
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const logoutMutation = useLogout();
  const isEn = i18n.language?.startsWith('en');
  const isPatient = user?.role === 'PATIENT';

  const navLinks = useMemo(
    () => [
      { label: t('nav.home'), href: '/' },
      { label: t('nav.specialties'), href: '/specialties' },
      { label: t('nav.doctors'), href: '/doctors' },
      { label: t('nav.services'), href: '/medical-services' },
      { label: t('nav.branches'), href: '/branches' },
      { label: t('nav.availability'), href: '/availability' },
      { label: t('nav.lookup', { defaultValue: isEn ? 'Lookup' : 'Tra cứu' }), href: '/appointments/lookup' },
    ],
    [isEn, t],
  );

  const patientLinks = [
    { label: isEn ? 'My profile' : 'Hồ sơ cá nhân', href: '/me', icon: UserRound },
    { label: isEn ? 'My appointments' : 'Phiếu hẹn của tôi', href: '/me/appointments', icon: CalendarCheck2 },
    { label: isEn ? 'My results' : 'Kết quả của tôi', href: '/me/results', icon: FileSearch },
    { label: isEn ? 'My invoices' : 'Hóa đơn của tôi', href: '/me/invoices', icon: Receipt },
  ];

  const closeMobile = () => setMobileOpen(false);

  const renderUserMenu = (mobile = false) => {
    if (!isAuthenticated || !user) {
      return mobile ? (
        <div className="mt-3 grid grid-cols-2 gap-2">
          <Button asChild variant="outline" size="sm" className="rounded-lg">
            <Link to="/login" onClick={closeMobile}>{t('nav.login')}</Link>
          </Button>
          <Button asChild size="sm" className="rounded-lg">
            <Link to="/booking" onClick={closeMobile}>{t('nav.booking')}</Link>
          </Button>
        </div>
      ) : (
        <div className="hidden items-center gap-2 md:flex">
          <Button asChild variant="ghost" size="sm" className="rounded-lg px-3 font-semibold">
            <Link to="/login">{t('nav.login')}</Link>
          </Button>
          <Button asChild size="sm" className="rounded-lg px-4 font-semibold shadow-elevated">
            <Link to="/booking">{t('nav.booking')}</Link>
          </Button>
        </div>
      );
    }

    const accountLinks = isPatient
      ? patientLinks
      : [{ label: isEn ? 'Open internal system' : 'Vào hệ thống nội bộ', href: '/app/dashboard', icon: Shield }];

    if (mobile) {
      return (
        <div className="mt-3 rounded-xl border bg-card p-3 shadow-card">
          <div className="mb-3 flex items-center gap-3">
            <UserAvatar name={user.fullName || 'User'} avatarUrl={user.avatarUrl} size="md" />
            <div className="min-w-0">
              <p className="truncate font-medium text-foreground">{user.fullName}</p>
              <p className="truncate text-xs text-muted-foreground">
                {user.email || (isPatient ? (isEn ? 'My PrimeCare account' : 'Tài khoản PrimeCare của tôi') : user.role)}
              </p>
            </div>
          </div>
          <div className="flex flex-col gap-1">
            {accountLinks.map((item) => {
              const Icon = item.icon;
              return (
                <Link key={item.href} to={item.href} onClick={closeMobile} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-foreground hover:bg-muted">
                  <Icon className="h-4 w-4 text-muted-foreground" />
                  {item.label}
                </Link>
              );
            })}
            <button
              type="button"
              onClick={() => {
                closeMobile();
                logoutMutation.mutate();
              }}
              className="flex items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-destructive hover:bg-destructive/5"
            >
              <LogOut className="h-4 w-4" />
              {isEn ? 'Sign out' : 'Đăng xuất'}
            </button>
          </div>
        </div>
      );
    }

    return (
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="hidden items-center gap-2 rounded-lg border border-border/70 bg-background/80 px-2 py-1.5 transition-colors hover:bg-muted md:flex">
            <UserAvatar name={user.fullName || 'User'} avatarUrl={user.avatarUrl} size="sm" />
            <div className="max-w-32 text-left">
              <p className="truncate text-sm font-medium text-foreground">{user.fullName}</p>
              <p className="truncate text-xs text-muted-foreground">{isPatient ? (isEn ? 'Patient portal' : 'Hồ sơ bệnh nhân') : (isEn ? 'System account' : 'Tài khoản hệ thống')}</p>
            </div>
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-64 rounded-xl p-2">
          <DropdownMenuLabel className="px-3 py-2">
            <div className="flex items-center gap-3">
              <UserAvatar name={user.fullName || 'User'} avatarUrl={user.avatarUrl} size="md" />
              <div className="min-w-0">
                <p className="truncate font-medium text-foreground">{user.fullName}</p>
                <p className="truncate text-xs text-muted-foreground">
                  {user.email || (isPatient ? (isEn ? 'My PrimeCare account' : 'Tài khoản PrimeCare của tôi') : user.role)}
                </p>
              </div>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          {accountLinks.map((item) => {
            const Icon = item.icon;
            return (
              <DropdownMenuItem asChild key={item.href}>
                <Link to={item.href} className="rounded-lg">
                  <Icon className="mr-2 h-4 w-4" />
                  {item.label}
                </Link>
              </DropdownMenuItem>
            );
          })}
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => logoutMutation.mutate()} className="rounded-lg text-destructive focus:text-destructive">
            <LogOut className="mr-2 h-4 w-4" />
            {isEn ? 'Sign out' : 'Đăng xuất'}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border/60 bg-background/85 backdrop-blur-md">
      <div className="container-page flex h-16 items-center justify-between gap-3">
        <Link to="/" className="flex min-w-0 items-center gap-2">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl gradient-hero shadow-elevated">
            <Activity className="h-5 w-5 text-primary-foreground" strokeWidth={2.5} />
          </div>
          <div className="leading-tight">
            <div className="text-lg font-bold tracking-tight text-foreground">PrimeCare</div>
            <div className="text-[10px] uppercase tracking-wider text-muted-foreground">{isEn ? 'Thoughtful care' : 'Chăm sóc tận tâm'}</div>
          </div>
        </Link>

        <nav className="hidden flex-1 items-center justify-center gap-1 lg:flex">
          {navLinks.map((link) => (
            <NavLink
              key={link.href}
              to={link.href}
              end={link.href === '/'}
              className={({ isActive }) =>
                cn(
                  'rounded-lg px-2.5 py-2 text-sm font-medium transition-colors xl:px-3',
                  isActive ? 'bg-primary-soft text-primary' : 'text-foreground/80 hover:bg-muted hover:text-foreground',
                )
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="flex shrink-0 items-center gap-1.5">
          <LanguageSwitcher />
          <button onClick={toggleTheme} className="rounded-lg p-2 transition-colors hover:bg-muted" aria-label="Toggle theme" type="button">
            {theme === 'light' ? <Moon className="h-[18px] w-[18px]" /> : <Sun className="h-[18px] w-[18px]" />}
          </button>
          {renderUserMenu()}
          <button className="rounded-lg p-2 transition-colors hover:bg-muted lg:hidden" onClick={() => setMobileOpen(!mobileOpen)} aria-label="Menu" type="button">
            {mobileOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <div className="border-t border-border bg-background lg:hidden">
          <div className="container-page flex flex-col gap-1 py-3">
            {navLinks.map((link) => (
              <NavLink
                key={link.href}
                to={link.href}
                end={link.href === '/'}
                onClick={closeMobile}
                className={({ isActive }) =>
                  cn('rounded-lg px-3 py-2 text-sm font-medium transition-colors', isActive ? 'bg-primary-soft text-primary' : 'text-foreground/80 hover:bg-muted')
                }
              >
                {link.label}
              </NavLink>
            ))}
            {renderUserMenu(true)}
          </div>
        </div>
      )}
    </header>
  );
}
