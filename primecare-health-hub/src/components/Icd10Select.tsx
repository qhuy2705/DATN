import { useMemo, useState } from 'react';
import { useIcd10Codes } from '@/hooks/use-doctor-data';
import type { Icd10CodeResponse } from '@/types/api';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { Button } from '@/components/ui/button';
import { ChevronsUpDown, Loader2 } from 'lucide-react';
import { useDebouncedValue } from '@/hooks/use-debounced-value';

export function Icd10Select({
  onSelect,
  disabled,
}: {
  onSelect: (code: Icd10CodeResponse) => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search.trim(), 400);
  const hasSearch = debouncedSearch.length > 0;
  const queryParams = useMemo(
    () => (hasSearch ? { q: debouncedSearch, size: '20' } : undefined),
    [debouncedSearch, hasSearch],
  );

  const { data, isLoading, isFetching } = useIcd10Codes(queryParams);
  const items = data?.items ?? [];

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between"
          disabled={disabled}
        >
          Chọn mã ICD-10...
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-full p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput 
            placeholder="Tìm mã ICD-10 (mã hoặc tên)..." 
            value={search}
            onValueChange={setSearch}
          />
          <CommandList>
            {!search.trim() ? (
              <div className="p-4 text-center text-sm text-muted-foreground">
                Nhập tên bệnh hoặc mã ICD-10 để tìm kiếm
              </div>
            ) : (isLoading || isFetching) && items.length === 0 ? (
              <div className="p-4 flex items-center justify-center text-muted-foreground text-sm">
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Đang tìm...
              </div>
            ) : items.length === 0 ? (
              <CommandEmpty>Không tìm thấy mã ICD-10 phù hợp</CommandEmpty>
            ) : (
              <CommandGroup>
                {items.map((item) => (
                  <CommandItem
                    key={item.id}
                    value={item.code}
                    onSelect={() => {
                      onSelect(item);
                      setOpen(false);
                      setSearch('');
                    }}
                  >
                    <span className="font-medium mr-2">{item.code}</span>
                    <span className="text-muted-foreground truncate">{item.nameVn}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
