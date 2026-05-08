import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useTranslation } from 'react-i18next';

export interface FilterOption {
  label: string;
  value: string;
}

export interface FilterConfig {
  key: string;
  label: string;
  options: FilterOption[];
  value: string;
  onChange: (v: string) => void;
}

interface FilterBarProps {
  filters: FilterConfig[];
}

export function FilterBar({ filters }: FilterBarProps) {
  const { t } = useTranslation();

  return (
    <div className="mb-ui-md flex flex-wrap gap-ui-sm">
      {filters.map((f) => (
        <div key={f.key} className="min-w-[180px] space-y-ui-xs">
          <p id={`${f.key}-filter-label`} className="text-xs font-medium text-muted-foreground">{f.label}</p>
          <Select value={f.value} onValueChange={f.onChange}>
            <SelectTrigger className="h-9 text-sm" aria-labelledby={`${f.key}-filter-label`}>
              <SelectValue placeholder={`${f.label}: ${t('common.all')}`} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">{t('common.all')}</SelectItem>
              {f.options.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      ))}
    </div>
  );
}
