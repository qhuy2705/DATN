import { Link, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, Calendar, Users, Building2, Stethoscope, Pill, Boxes,
  UserCog, ClipboardList, Receipt, Clock, CalendarOff,
  Activity, ShieldCheck, FlaskConical,
  PhoneCall, ShieldAlert, Gauge,
} from 'lucide-react';
import {
  Sidebar, SidebarContent, SidebarGroup, SidebarGroupContent,
  SidebarGroupLabel, SidebarMenu, SidebarMenuButton, SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar';
import { useAuthStore } from '@/stores/auth-store';
import { useTranslation } from 'react-i18next';
import type { AppRole } from '@/types/api';
import { DEFAULT_ROLE_HOME, ROUTE_ROLES } from '@/lib/route-access';
import { cn } from '@/lib/utils';

interface MenuItem {
  titleKey: string;
  url: string;
  icon: React.ElementType;
  roles: AppRole[];
}

interface MenuGroup {
  labelKey: string;
  items: MenuItem[];
}

const menuGroups: MenuGroup[] = [
  {
    labelKey: 'sidebar.overview',
    items: [
      { titleKey: 'sidebar.dashboard', url: '/app/dashboard', icon: LayoutDashboard, roles: ROUTE_ROLES.dashboard },
    ],
  },
  {
    labelKey: 'sidebar.systemMgmt',
    items: [
      { titleKey: 'sidebar.branches', url: '/app/admin/branches', icon: Building2, roles: ROUTE_ROLES.branches },
      { titleKey: 'sidebar.specialties', url: '/app/admin/specialties', icon: Stethoscope, roles: ROUTE_ROLES.specialties },
      { titleKey: 'sidebar.branchSpecialties', url: '/app/admin/branch-specialties', icon: Activity, roles: ROUTE_ROLES.branchSpecialties },
      { titleKey: 'sidebar.doctors', url: '/app/admin/doctors', icon: UserCog, roles: ROUTE_ROLES.doctors },
      { titleKey: 'sidebar.staff', url: '/app/admin/staffs', icon: Users, roles: ROUTE_ROLES.staffs },
      { titleKey: 'sidebar.patients', url: '/app/admin/patients', icon: Users, roles: ROUTE_ROLES.patients },
      { titleKey: 'sidebar.medications', url: '/app/admin/medications', icon: Pill, roles: ROUTE_ROLES.medications },
      { titleKey: 'sidebar.medicalServices', url: '/app/admin/medical-services', icon: ClipboardList, roles: ROUTE_ROLES.medicalServices },
      { titleKey: 'sidebar.doctorSchedules', url: '/app/admin/doctor-schedules', icon: Clock, roles: ROUTE_ROLES.doctorSchedulesAdmin },
      { titleKey: 'sidebar.doctorLeaves', url: '/app/admin/doctor-leaves', icon: CalendarOff, roles: ROUTE_ROLES.doctorLeavesAdmin },
      { titleKey: 'sidebar.auditLogs', url: '/app/admin/audit-logs', icon: ShieldCheck, roles: ROUTE_ROLES.auditLogs },
      { titleKey: 'sidebar.rateLimits', url: '/app/admin/rate-limits', icon: Gauge, roles: ROUTE_ROLES.rateLimits },
    ],
  },
  {
    labelKey: 'sidebar.appointments',
    items: [
      { titleKey: 'sidebar.appointmentMgmt', url: '/app/appointments', icon: Calendar, roles: ROUTE_ROLES.appointments },
      { titleKey: 'sidebar.receptionQueue', url: '/app/reception/queue', icon: Users, roles: ROUTE_ROLES.receptionQueue },
      { titleKey: 'Cần xử lý sau lịch', url: '/app/appointment-follow-ups', icon: PhoneCall, roles: ROUTE_ROLES.appointmentFollowUps },
      { titleKey: 'Hạn chế đặt lịch', url: '/app/booking-restrictions', icon: ShieldAlert, roles: ROUTE_ROLES.bookingRestrictions },
      { titleKey: 'sidebar.walkIn', url: '/app/reception/walk-in', icon: ClipboardList, roles: ROUTE_ROLES.walkIn },
      { titleKey: 'sidebar.serviceDesk', url: '/app/service-desk/results', icon: FlaskConical, roles: ROUTE_ROLES.serviceDesk },
    ],
  },
  {
    labelKey: 'sidebar.doctorSection',
    items: [
      { titleKey: 'sidebar.myAppointments', url: '/app/doctor/appointments', icon: Calendar, roles: ROUTE_ROLES.doctor },
      { titleKey: 'sidebar.mySchedule', url: '/app/doctor/schedules', icon: Clock, roles: ROUTE_ROLES.doctor },
      { titleKey: 'sidebar.leaveRequests', url: '/app/doctor/leave-requests', icon: CalendarOff, roles: ROUTE_ROLES.doctor },
    ],
  },
  {
    labelKey: 'sidebar.cashier',
    items: [
      { titleKey: 'sidebar.invoices', url: '/app/cashier/invoices', icon: Receipt, roles: ROUTE_ROLES.cashier },
    ],
  },
  {
    labelKey: 'sidebar.pharmacy',
    items: [
      { titleKey: 'sidebar.pharmacyDispense', url: '/app/pharmacy/dispense', icon: Pill, roles: ROUTE_ROLES.pharmacy },
      { titleKey: 'sidebar.pharmacyInventory', url: '/app/pharmacy/inventory', icon: Boxes, roles: ROUTE_ROLES.pharmacy },
    ],
  },
];

export function InternalSidebar() {
  const { state } = useSidebar();
  const collapsed = state === 'collapsed';
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const { t } = useTranslation();

  const homeUrl = user ? (DEFAULT_ROLE_HOME[user.role] || '/app/dashboard') : '/app/dashboard';

  const visibleGroups = menuGroups
    .map((g) => ({
      ...g,
      items: g.items.filter((item) => user && item.roles.includes(user.role)),
    }))
    .filter((g) => g.items.length > 0);

  return (
    <Sidebar collapsible="icon" className="border-r border-sidebar-border bg-sidebar">
      <div className="border-b border-sidebar-border px-3 py-3">
        <Link to={homeUrl} className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-sidebar-primary">
            <span className="text-sm font-bold text-sidebar-primary-foreground">P</span>
          </div>
          {!collapsed && (
            <div className="min-w-0">
              <span className="block truncate text-sm font-semibold tracking-tight text-sidebar-foreground">PrimeCare</span>
              <span className="block truncate text-[11px] text-sidebar-foreground/55">Clinic operations</span>
            </div>
          )}
        </Link>
      </div>
      <SidebarContent className="gap-1 py-2">
        {visibleGroups.map((group) => (
          <SidebarGroup key={group.labelKey} className="px-2 py-1">
            {!collapsed && (
              <SidebarGroupLabel className="h-7 px-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-sidebar-foreground/50">
                {group.labelKey.startsWith('sidebar.') ? t(group.labelKey) : group.labelKey}
              </SidebarGroupLabel>
            )}
            <SidebarGroupContent>
              <SidebarMenu className="gap-0.5">
                {group.items.map((item) => {
                  const active = location.pathname === item.url || location.pathname.startsWith(item.url + '/');
                  return (
                    <SidebarMenuItem key={item.url}>
                      <SidebarMenuButton
                        asChild
                        isActive={active}
                        tooltip={collapsed ? t(item.titleKey) : undefined}
                        className={cn(
                          'h-8 rounded-md text-sidebar-foreground/75 hover:text-sidebar-accent-foreground',
                          active && 'text-sidebar-accent-foreground',
                        )}
                      >
                        <Link to={item.url}>
                          <item.icon className="h-4 w-4" />
                          {!collapsed && <span>{item.titleKey.startsWith('sidebar.') ? t(item.titleKey) : item.titleKey}</span>}
                        </Link>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
    </Sidebar>
  );
}
