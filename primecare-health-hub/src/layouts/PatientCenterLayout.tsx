import { Link, Outlet, useLocation } from 'react-router-dom';
import { CalendarDays, ChevronRight, FileText, LayoutDashboard, Receipt, UserRound } from 'lucide-react';
import { useCurrentUser } from '@/hooks/use-auth';
import { UserAvatar } from '@/components/UserAvatar';
import { cn } from '@/lib/utils';

const patientNav = [
  { label: 'Tóm tắt', href: '/me', icon: LayoutDashboard, match: ['/me'] },
  { label: 'Thông tin cá nhân', href: '/me/profile', icon: UserRound, match: ['/me/profile', '/me/preferences'] },
  { label: 'Lịch sử đặt lịch', href: '/me/appointments', icon: CalendarDays, match: ['/me/appointments'] },
  { label: 'Kết quả xét nghiệm', href: '/me/results', icon: FileText, match: ['/me/results'] },
  { label: 'Hóa đơn', href: '/me/invoices', icon: Receipt, match: ['/me/invoices'] },
];

export function PatientCenterLayout() {
  const location = useLocation();
  const user = useCurrentUser();

  return (
    <div className="bg-[radial-gradient(circle_at_top,_rgba(37,99,235,0.07),_transparent_40%),linear-gradient(180deg,#f8fafc_0%,#eef5ff_100%)]">
      <section className="container-wide py-8 md:py-10">
        <div className="rounded-[28px] border border-border/70 bg-background/90 p-4 shadow-sm backdrop-blur md:p-6">
          <div className="grid gap-6 xl:grid-cols-[280px_minmax(0,1fr)]">
            <aside className="rounded-[24px] border border-border/60 bg-slate-950 text-slate-100 shadow-xl">
              <div className="border-b border-white/10 px-5 py-6">
                <div className="mb-4 inline-flex rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-medium text-sky-200">
                  Personal Center
                </div>
                <div className="flex items-center gap-3">
                  <UserAvatar name={user?.fullName || 'Patient'} avatarUrl={user?.avatarUrl} size="lg" className="ring-slate-900" />
                  <div className="min-w-0">
                    <p className="truncate text-base font-semibold">{user?.fullName || 'Bệnh nhân PrimeCare'}</p>
                    <p className="truncate text-sm text-slate-400">{user?.email || 'Quản lý hồ sơ y tế cá nhân'}</p>
                  </div>
                </div>
                <p className="mt-4 text-sm leading-6 text-slate-400">
                  Xem nhanh lịch hẹn, kết quả, hóa đơn và cập nhật hồ sơ của bạn mà không rời khỏi website PrimeCare.
                </p>
              </div>

              <nav className="space-y-1 px-3 py-4">
                {patientNav.map((item) => {
                  const Icon = item.icon;
                  const isActive = item.match.includes(location.pathname);
                  return (
                    <Link
                      key={item.href}
                      to={item.href}
                      className={cn(
                        'flex items-center justify-between rounded-2xl px-3.5 py-3 text-sm transition-colors',
                        isActive
                          ? 'bg-amber-500/90 text-white shadow-lg shadow-amber-500/10'
                          : 'text-slate-300 hover:bg-white/5 hover:text-white',
                      )}
                    >
                      <span className="flex items-center gap-3">
                        <Icon className="h-4 w-4" />
                        {item.label}
                      </span>
                      <ChevronRight className="h-4 w-4 opacity-70" />
                    </Link>
                  );
                })}
              </nav>
            </aside>

            <div className="min-w-0 rounded-[24px] border border-border/60 bg-card/95 p-4 shadow-sm md:p-6">
              <Outlet />
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
