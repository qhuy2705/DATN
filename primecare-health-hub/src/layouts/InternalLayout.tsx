import { Outlet } from 'react-router-dom';
import { InternalSidebar } from '@/components/InternalSidebar';
import { InternalTopbar } from '@/components/InternalTopbar';
import { SidebarProvider } from '@/components/ui/sidebar';
import { useInternalRealtime } from '@/hooks/use-internal-realtime';

export function InternalLayout() {
  useInternalRealtime();

  return (
    <SidebarProvider>
      <div className="min-h-screen flex w-full bg-surface-alt">
        <InternalSidebar />
        <div className="flex-1 flex flex-col min-w-0">
          <InternalTopbar />
          <main className="flex-1 p-4 md:p-6 lg:p-8">
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarProvider>
  );
}
