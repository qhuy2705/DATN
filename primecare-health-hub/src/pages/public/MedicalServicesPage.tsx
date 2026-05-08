import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, FileText, Search } from 'lucide-react';
import { ScrollReveal } from '@/components/ScrollReveal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useMedicalServices } from '@/hooks/use-public-data';
import { useTranslation } from 'react-i18next';
import type { MedicalService } from '@/types/api';

function formatPrice(value: number | undefined, locale: string) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return null;

  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}

function getServiceGroup(service: MedicalService, isEn: boolean) {
  return service.groupName?.trim() || service.serviceType?.trim() || (isEn ? 'Other services' : 'Dịch vụ khác');
}

export default function MedicalServicesPage() {
  const { i18n } = useTranslation();
  const isEn = i18n.language.startsWith('en');
  const locale = isEn ? 'en-US' : 'vi-VN';
  const [search, setSearch] = useState('');
  const [selectedGroup, setSelectedGroup] = useState('all');
  const { data: services = [], isLoading, isError } = useMedicalServices();

  const groupOptions = useMemo(() => {
    const counts = new Map<string, number>();
    services.forEach((service) => {
      const group = getServiceGroup(service, isEn);
      counts.set(group, (counts.get(group) ?? 0) + 1);
    });

    return Array.from(counts.entries())
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([label, count]) => ({ label, count }));
  }, [isEn, services]);

  useEffect(() => {
    if (selectedGroup === 'all') return;
    if (groupOptions.some((group) => group.label === selectedGroup)) return;
    setSelectedGroup('all');
  }, [groupOptions, selectedGroup]);

  const visibleServices = useMemo(() => {
    const query = search.trim().toLowerCase();

    return services.filter((service) => {
      const group = getServiceGroup(service, isEn);
      const matchesGroup = selectedGroup === 'all' || group === selectedGroup;
      const searchableText = [
        service.name,
        service.description,
        service.code,
        service.serviceType,
        service.departmentCode,
        group,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();

      return matchesGroup && (!query || searchableText.includes(query));
    });
  }, [isEn, search, selectedGroup, services]);

  const hasSearchOrFilter = Boolean(search.trim() || selectedGroup !== 'all');

  return (
    <div className="public-page">
      <div className="container-page py-12">
        <div className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="public-page-title">{isEn ? 'Medical services' : 'Dịch vụ cận lâm sàng'}</h1>
            <p className="public-page-subtitle">
              {isEn ? 'Search laboratory, imaging, and clinical service prices.' : 'Bảng giá xét nghiệm và chẩn đoán hình ảnh.'}
            </p>
          </div>
          <Button asChild className="w-fit rounded-lg">
            <Link to="/booking">
              {isEn ? 'Book appointment' : 'Đặt lịch ngay'}
              <CalendarDays className="ml-2 h-4 w-4" />
            </Link>
          </Button>
        </div>

        <ScrollReveal className="mb-6">
          <div className="space-y-4">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div className="relative w-full md:max-w-md">
                <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="service-search"
                  placeholder={isEn ? 'Search services...' : 'Tìm dịch vụ...'}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  className="h-11 rounded-lg bg-card pl-11"
                />
              </div>
              <div className="inline-flex w-fit items-center gap-2 rounded-full bg-primary-soft px-3 py-1.5 text-sm font-medium text-primary">
                <FileText className="h-4 w-4" />
                {isEn ? `${visibleServices.length} services` : `${visibleServices.length} dịch vụ`}
              </div>
            </div>

            {groupOptions.length > 0 ? (
              <div className="flex gap-2 overflow-x-auto pb-1">
                <button
                  type="button"
                  onClick={() => setSelectedGroup('all')}
                  className={`whitespace-nowrap rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                    selectedGroup === 'all'
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-card text-muted-foreground hover:bg-primary-soft hover:text-primary'
                  }`}
                >
                  {isEn ? 'All' : 'Tất cả'} ({services.length})
                </button>
                {groupOptions.map((group) => (
                  <button
                    key={group.label}
                    type="button"
                    onClick={() => setSelectedGroup(group.label)}
                    className={`whitespace-nowrap rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                      selectedGroup === group.label
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-card text-muted-foreground hover:bg-primary-soft hover:text-primary'
                    }`}
                  >
                    {group.label} ({group.count})
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        </ScrollReveal>

        {isLoading ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center text-muted-foreground">
            {isEn ? 'Loading services...' : 'Đang tải dịch vụ...'}
          </div>
        ) : isError ? (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <p className="text-lg font-semibold text-foreground">
              {isEn ? 'Services are unavailable right now.' : 'Hiện chưa tải được danh sách dịch vụ.'}
            </p>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
              {isEn ? 'You can still book an appointment or contact the clinic for guidance.' : 'Bạn vẫn có thể đặt lịch hoặc liên hệ cơ sở để được hướng dẫn.'}
            </p>
          </div>
        ) : visibleServices.length > 0 ? (
          <ScrollReveal>
            <div className="public-page-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px] text-sm">
                  <thead className="border-b border-border bg-muted/30 text-left">
                    <tr>
                      <th className="px-4 py-3 font-semibold text-foreground">{isEn ? 'Code' : 'Mã'}</th>
                      <th className="px-4 py-3 font-semibold text-foreground">{isEn ? 'Service name' : 'Tên dịch vụ'}</th>
                      <th className="px-4 py-3 font-semibold text-foreground">{isEn ? 'Type' : 'Loại'}</th>
                      <th className="px-4 py-3 text-right font-semibold text-foreground">{isEn ? 'Price' : 'Giá'}</th>
                      <th className="px-4 py-3 text-right font-semibold text-foreground">{isEn ? 'Action' : 'Thao tác'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleServices.map((service) => {
                      const group = getServiceGroup(service, isEn);
                      const price = formatPrice(service.price, locale);
                      return (
                        <tr key={service.id} className="border-b border-border last:border-0 hover:bg-muted/20">
                          <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{service.code || service.departmentCode || '—'}</td>
                          <td className="px-4 py-3">
                            <div className="font-medium text-foreground">{service.name}</div>
                            {service.description ? <div className="mt-1 max-w-xl truncate text-xs text-muted-foreground">{service.description}</div> : null}
                          </td>
                          <td className="px-4 py-3">
                            <span className="rounded-full bg-primary-soft px-2 py-0.5 text-xs font-medium text-primary">{group}</span>
                          </td>
                          <td className="px-4 py-3 text-right font-semibold text-foreground">{price || (isEn ? 'Contact' : 'Liên hệ')}</td>
                          <td className="px-4 py-3 text-right">
                            <div className="inline-flex items-center gap-2">
                              <Button asChild variant="ghost" size="sm" className="rounded-lg px-3 text-primary hover:bg-primary-soft hover:text-primary">
                                <Link to={`/medical-services/${service.id}`}>
                                  {isEn ? 'Details' : 'Chi tiết'}
                                  <ArrowRight className="ml-1.5 h-4 w-4" />
                                </Link>
                              </Button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </ScrollReveal>
        ) : (
          <div className="public-page-card border-dashed px-6 py-14 text-center">
            <p className="text-lg font-semibold text-foreground">
              {hasSearchOrFilter
                ? isEn
                  ? 'No services match your search.'
                  : 'Không tìm thấy dịch vụ phù hợp.'
                : isEn
                  ? 'No services are published yet.'
                  : 'Chưa có dịch vụ công khai.'}
            </p>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
              {hasSearchOrFilter
                ? isEn
                  ? 'Try a broader keyword or choose another category.'
                  : 'Thử từ khóa rộng hơn hoặc chọn nhóm dịch vụ khác.'
                : isEn
                  ? 'Please contact the clinic if you need help choosing care.'
                  : 'Vui lòng liên hệ cơ sở nếu bạn cần hỗ trợ chọn dịch vụ.'}
            </p>
            {hasSearchOrFilter ? (
              <Button
                type="button"
                variant="outline"
                className="mt-6 rounded-lg"
                onClick={() => {
                  setSearch('');
                  setSelectedGroup('all');
                }}
              >
                {isEn ? 'Clear filters' : 'Xóa bộ lọc'}
              </Button>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}
