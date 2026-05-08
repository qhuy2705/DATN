import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, CalendarDays, Search } from 'lucide-react';
import { ScrollReveal } from '@/components/ScrollReveal';
import { BranchCard } from '@/components/BranchCard';
import { AppPagination } from '@/components/AppPagination';
import { useBranchesPage } from '@/hooks/use-public-data';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useTranslation } from 'react-i18next';

export default function BranchesPage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);
  const pageSize = 9;

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const { data: branchPage, isLoading } = useBranchesPage({
    page: String(page),
    size: String(pageSize),
    ...(debouncedSearch ? { q: debouncedSearch } : {}),
  });

  const branches = branchPage?.items ?? [];
  const meta = branchPage?.meta;
  const totalItems = meta?.totalItems ?? 0;

  return (
    <div className="public-page">
      <div className="container-page py-12">
        <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="public-page-title">{isEn ? 'Branches' : 'Hệ thống cơ sở'}</h1>
            <p className="public-page-subtitle">
              {isEn ? 'Find a clinic location, contact details, and availability.' : 'Tìm cơ sở, kiểm tra liên hệ, rồi đặt lịch hoặc xem lịch trống.'}
            </p>
          </div>
          <Button asChild className="w-fit rounded-lg">
            <Link to="/booking">
              {isEn ? 'Book appointment' : 'Đặt lịch khám'}
              <CalendarDays className="ml-2 h-4 w-4" />
            </Link>
          </Button>
        </div>

        <ScrollReveal className="mb-6">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="relative w-full md:max-w-md">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="branch-search"
                placeholder={isEn ? 'Search branch or address...' : 'Tìm cơ sở hoặc địa chỉ...'}
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-11 rounded-lg bg-card pl-11"
              />
            </div>
            <div className="inline-flex w-fit items-center gap-2 rounded-full bg-primary-soft px-3 py-1.5 text-sm font-medium text-primary">
              <Building2 className="h-4 w-4" />
              {isEn ? `${totalItems} branches` : `${totalItems} cơ sở`}
            </div>
          </div>
        </ScrollReveal>

        {isLoading ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center text-muted-foreground">
            {isEn ? 'Loading branches...' : 'Đang tải cơ sở...'}
          </div>
        ) : branches.length > 0 ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {branches.map((branch, index) => (
                <ScrollReveal key={branch.id} delay={index * 40}>
                  <BranchCard branch={branch} />
                </ScrollReveal>
              ))}
            </div>

            <AppPagination
              page={meta?.page ?? 0}
              totalPages={meta?.totalPages ?? 0}
              onPageChange={setPage}
              className="mt-10"
            />
          </>
        ) : (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <p className="text-lg font-semibold text-foreground">
              {isEn ? 'No branches found.' : 'Không tìm thấy cơ sở phù hợp.'}
            </p>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
              {isEn ? 'Try a broader location keyword or clear search.' : 'Thử từ khóa địa điểm rộng hơn hoặc xóa tìm kiếm.'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
