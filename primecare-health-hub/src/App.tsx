import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes, useSearchParams } from 'react-router-dom';
import { Toaster as Sonner } from '@/components/ui/sonner';
import { TooltipProvider } from '@/components/ui/tooltip';
import { PublicLayout } from '@/layouts/PublicLayout';
import { InternalLayout } from '@/layouts/InternalLayout';
import { PatientCenterLayout } from '@/layouts/PatientCenterLayout';
import { RouteGuard } from '@/components/RouteGuard';
import NotFound from './pages/NotFound.tsx';

import HomePage from '@/pages/public/HomePage';
import DoctorsPage from '@/pages/public/DoctorsPage';
import DoctorDetailPage from '@/pages/public/DoctorDetailPage';
import SpecialtiesPage from '@/pages/public/SpecialtiesPage';
import BranchesPage from '@/pages/public/BranchesPage';
import MedicalServicesPage from '@/pages/public/MedicalServicesPage';
import MedicalServiceDetailPage from '@/pages/public/MedicalServiceDetailPage';
import AvailabilityPage from '@/pages/public/AvailabilityPage';
import BookingPage from '@/pages/public/BookingPage';
import BookingSuccessPage from '@/pages/public/BookingSuccessPage';
import AppointmentLookupPage from '@/pages/public/AppointmentLookupPage';
import AppointmentResponsePage from '@/pages/public/AppointmentResponsePage';
import RescheduleOfferPage from '@/pages/public/RescheduleOfferPage';
import ResultLookupPage from '@/pages/public/ResultLookupPage';
import AboutPage from '@/pages/public/AboutPage';
import FaqPage from '@/pages/public/FaqPage';
import ContactPage from '@/pages/public/ContactPage';

import LoginPage from '@/pages/LoginPage';
import ForgotPasswordPage from '@/pages/ForgotPasswordPage';
import SetPasswordPage from '@/pages/SetPasswordPage';
import RegisterPatientAccountPage from '@/pages/RegisterPatientAccountPage';

import DashboardPage from '@/pages/internal/DashboardPage';
import BranchesAdminPage from '@/pages/internal/BranchesAdminPage';
import SpecialtiesAdminPage from '@/pages/internal/SpecialtiesAdminPage';
import BranchSpecialtiesPage from '@/pages/internal/BranchSpecialtiesPage';
import DoctorsAdminPage from '@/pages/internal/DoctorsAdminPage';
import StaffsPage from '@/pages/internal/StaffsPage';
import PatientsPage from '@/pages/internal/PatientsPage';
import MedicationsPage from '@/pages/internal/MedicationsPage';
import MedicalServicesAdminPage from '@/pages/internal/MedicalServicesAdminPage';
import DoctorSchedulesAdminPage from '@/pages/internal/DoctorSchedulesAdminPage';
import DoctorLeavesAdminPage from '@/pages/internal/DoctorLeavesAdminPage';
import AuditLogsPage from '@/pages/internal/AuditLogsPage';
import RateLimitsPage from '@/pages/internal/RateLimitsPage';
import AppointmentsPage from '@/pages/internal/AppointmentsPage';
import AppointmentProcessingPage from '@/pages/internal/AppointmentProcessingPage';
import AppointmentFollowUpsPage from '@/pages/internal/AppointmentFollowUpsPage';
import ReceptionQueuePage from '@/pages/internal/ReceptionQueuePage';
import WalkInPage from '@/pages/internal/WalkInPage';
import DoctorAppointmentsPage from '@/pages/internal/DoctorAppointmentsPage';
import EncounterDetailPage from '@/pages/internal/EncounterDetailPage';
import DoctorSchedulesViewPage from '@/pages/internal/DoctorSchedulesViewPage';
import LeaveRequestsPage from '@/pages/internal/LeaveRequestsPage';
import InvoicesPage from '@/pages/internal/InvoicesPage';
import InvoicePdfJobPage from '@/pages/internal/InvoicePdfJobPage';
import AccountSettingsPage from '@/pages/internal/AccountSettingsPage';
import ServiceDeskPage from '@/pages/internal/ServiceDeskPage';
import PatientOverviewPage from '@/pages/patient/PatientOverviewPage';
import PatientProfilePage from '@/pages/patient/PatientProfilePage';
import PatientAppointmentsPage from '@/pages/patient/PatientAppointmentsPage';
import PatientResultsPage from '@/pages/patient/PatientResultsPage';
import PatientInvoicesPage from '@/pages/patient/PatientInvoicesPage';
import PharmacyDispensePage from '@/pages/internal/PharmacyDispensePage';
import PharmacyInventoryPage from '@/pages/internal/PharmacyInventoryPage';
import NotificationsAdminPage from '@/pages/internal/NotificationsAdminPage';
import BookingRestrictionsPage from '@/pages/internal/BookingRestrictionsPage';
import { ROUTE_ROLES } from '@/lib/route-access';
import { useSessionBootstrap } from '@/hooks/use-session-bootstrap';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

function SessionBootstrap() {
  useSessionBootstrap();
  return null;
}

function DoctorPrescriptionsRedirect() {
  const [searchParams] = useSearchParams();
  const encounterId = searchParams.get('encounterId')?.trim();

  return (
    <Navigate
      to={encounterId ? `/app/doctor/encounters/${encodeURIComponent(encounterId)}` : '/app/doctor/appointments'}
      replace
    />
  );
}

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Sonner position="top-right" />
      <BrowserRouter>
        <SessionBootstrap />
        <Routes>
          <Route element={<PublicLayout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/doctors" element={<DoctorsPage />} />
            <Route path="/doctors/:id" element={<DoctorDetailPage />} />
            <Route path="/specialties" element={<SpecialtiesPage />} />
            <Route path="/branches" element={<BranchesPage />} />
            <Route path="/medical-services" element={<MedicalServicesPage />} />
            <Route path="/medical-services/:id" element={<MedicalServiceDetailPage />} />
            <Route path="/availability" element={<AvailabilityPage />} />
            <Route path="/booking" element={<BookingPage />} />
            <Route path="/booking/success" element={<BookingSuccessPage />} />
            <Route path="/appointments/lookup" element={<AppointmentLookupPage />} />
            <Route path="/appointment-response/:token" element={<AppointmentResponsePage />} />
            <Route path="/public/reschedule/:token" element={<RescheduleOfferPage />} />
            <Route path="/results/lookup" element={<ResultLookupPage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="/faq" element={<FaqPage />} />
            <Route path="/contact" element={<ContactPage />} />

            <Route element={<RouteGuard roles={ROUTE_ROLES.patientPortal} />}>
              <Route element={<PatientCenterLayout />}>
                <Route path="/me" element={<PatientOverviewPage />} />
                <Route path="/me/profile" element={<PatientProfilePage />} />
                <Route path="/me/appointments" element={<PatientAppointmentsPage />} />
                <Route path="/me/results" element={<PatientResultsPage />} />
                <Route path="/me/invoices" element={<PatientInvoicesPage />} />
                <Route path="/me/preferences" element={<Navigate to="/me/profile?tab=notifications" replace />} />
              </Route>
            </Route>
          </Route>

          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/set-password" element={<SetPasswordPage />} />
          <Route path="/register" element={<RegisterPatientAccountPage />} />

          <Route element={<RouteGuard />}>
            <Route element={<InternalLayout />}>
              <Route element={<RouteGuard roles={ROUTE_ROLES.dashboard} />}>
                <Route path="/app/dashboard" element={<DashboardPage />} />
              </Route>

              <Route element={<RouteGuard roles={ROUTE_ROLES.branches} />}>
                <Route path="/app/admin/branches" element={<BranchesAdminPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.specialties} />}>
                <Route path="/app/admin/specialties" element={<SpecialtiesAdminPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.branchSpecialties} />}>
                <Route path="/app/admin/branch-specialties" element={<BranchSpecialtiesPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.doctors} />}>
                <Route path="/app/admin/doctors" element={<DoctorsAdminPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.staffs} />}>
                <Route path="/app/admin/staffs" element={<StaffsPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.patients} />}>
                <Route path="/app/admin/patients" element={<PatientsPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.medications} />}>
                <Route path="/app/admin/medications" element={<MedicationsPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.medicalServices} />}>
                <Route path="/app/admin/medical-services" element={<MedicalServicesAdminPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.doctorSchedulesAdmin} />}>
                <Route path="/app/admin/doctor-schedules" element={<DoctorSchedulesAdminPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.doctorLeavesAdmin} />}>
                <Route path="/app/admin/doctor-leaves" element={<DoctorLeavesAdminPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.auditLogs} />}>
                <Route path="/app/admin/audit-logs" element={<AuditLogsPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.rateLimits} />}>
                <Route path="/app/admin/rate-limits" element={<RateLimitsPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.notifications} />}>
                <Route path="/app/admin/notifications" element={<NotificationsAdminPage />} />
              </Route>

              <Route element={<RouteGuard roles={ROUTE_ROLES.appointments} />}>
                <Route path="/app/appointments" element={<AppointmentsPage />} />
                <Route path="/app/appointments/:id/process" element={<AppointmentProcessingPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.bookingRestrictions} />}>
                <Route path="/app/booking-restrictions" element={<BookingRestrictionsPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.appointmentFollowUps} />}>
                <Route path="/app/appointment-follow-ups" element={<AppointmentFollowUpsPage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.receptionQueue} />}>
                <Route path="/app/reception/queue" element={<ReceptionQueuePage />} />
              </Route>
              <Route element={<RouteGuard roles={ROUTE_ROLES.walkIn} />}>
                <Route path="/app/reception/walk-in" element={<WalkInPage />} />
              </Route>

              <Route element={<RouteGuard roles={ROUTE_ROLES.serviceDesk} />}>
                <Route path="/app/service-desk/results" element={<ServiceDeskPage />} />
              </Route>

              <Route element={<RouteGuard roles={ROUTE_ROLES.doctor} />}>
                <Route path="/app/doctor/appointments" element={<DoctorAppointmentsPage />} />
                <Route path="/app/doctor/encounters/:id" element={<EncounterDetailPage />} />
                <Route path="/app/doctor/prescriptions" element={<DoctorPrescriptionsRedirect />} />
                <Route path="/app/doctor/schedules" element={<DoctorSchedulesViewPage />} />
                <Route path="/app/doctor/leave-requests" element={<LeaveRequestsPage />} />
              </Route>

              <Route path="/app/account" element={<AccountSettingsPage />} />

              <Route element={<RouteGuard roles={ROUTE_ROLES.cashier} />}>
                <Route path="/app/cashier/invoices" element={<InvoicesPage />} />
                <Route path="/app/cashier/invoice-pdf-jobs/:jobId" element={<InvoicePdfJobPage />} />
              </Route>

              <Route element={<RouteGuard roles={ROUTE_ROLES.pharmacy} />}>
                <Route path="/app/pharmacy/dispense" element={<PharmacyDispensePage />} />
                <Route path="/app/pharmacy/inventory" element={<PharmacyInventoryPage />} />
              </Route>
            </Route>
          </Route>

          <Route path="/app/patient/overview" element={<Navigate to="/me" replace />} />
          <Route path="/app/patient/profile" element={<Navigate to="/me/profile" replace />} />
          <Route path="/app/patient/appointments" element={<Navigate to="/me/appointments" replace />} />
          <Route path="/app/patient/results" element={<Navigate to="/me/results" replace />} />
          <Route path="/app/patient/invoices" element={<Navigate to="/me/invoices" replace />} />
          <Route path="/app/patient/preferences" element={<Navigate to="/me/profile?tab=notifications" replace />} />

          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
