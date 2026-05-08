import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePatientTimeline } from '@/hooks/use-doctor-data';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Clock, Activity, Pill, FlaskConical } from 'lucide-react';
import { StatusBadge } from '@/components/StatusBadge';

interface PatientTimelineModalProps {
  patientId: string;
  patientName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function PatientTimelineModal({ patientId, patientName, open, onOpenChange }: PatientTimelineModalProps) {
  const { t } = useTranslation();
  const [filterType, setFilterType] = useState('__all__');

  const { data: timeline = [], isLoading } = usePatientTimeline(open ? patientId : undefined, filterType);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col overflow-hidden">
        <DialogHeader className="shrink-0">
          <DialogTitle>Lịch sử khám bệnh - {patientName}</DialogTitle>
        </DialogHeader>
        
        <div className="flex gap-2 mb-4 shrink-0 mt-2">
          <Select value={filterType} onValueChange={setFilterType}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="Loại sự kiện" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">Tất cả</SelectItem>
              <SelectItem value="ENCOUNTER">Lượt khám</SelectItem>
              <SelectItem value="PRESCRIPTION">Đơn thuốc</SelectItem>
              <SelectItem value="SERVICE_RESULT">Dịch vụ Cận lâm sàng</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex-1 overflow-y-auto pl-2 pr-4 pb-4">
          {isLoading ? (
            <div className="py-8 text-center text-muted-foreground">Đang tải lịch sử...</div>
          ) : timeline.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">Không có dữ liệu lịch sử</div>
          ) : (
            <div className="relative border-l border-border/50 ml-4 space-y-6">
              {timeline.map((event, idx) => {
                let Icon = Activity;
                if (event.type === 'ENCOUNTER') Icon = Clock;
                if (event.type === 'PRESCRIPTION') Icon = Pill;
                if (event.type === 'SERVICE_RESULT') Icon = FlaskConical;

                return (
                  <div key={`${event.referenceId}-${idx}`} className="relative pl-6">
                    <div className="absolute -left-3.5 top-1 bg-background border p-1 rounded-full text-muted-foreground shadow-sm">
                      <Icon className="h-4 w-4" />
                    </div>
                    <div className="rounded-lg border bg-card p-4 shadow-sm hover:shadow-md transition-shadow">
                      <div className="flex justify-between items-start mb-2 gap-4">
                        <div>
                          <h4 className="font-semibold text-sm text-foreground">{event.title}</h4>
                          <p className="text-xs text-muted-foreground mt-0.5">{event.subtitle}</p>
                        </div>
                        <div className="shrink-0">
                          <StatusBadge status={event.status} />
                        </div>
                      </div>
                      <p className="text-sm text-muted-foreground mt-2 line-clamp-3 leading-relaxed">
                        {event.description}
                      </p>
                      <div className="flex items-center gap-2 mt-3 pt-3 border-t text-xs text-muted-foreground">
                        <span>{new Date(event.occurredAt).toLocaleString('vi-VN')}</span>
                        {event.performedBy && (
                          <>
                            <span>•</span>
                            <span>{event.performedBy}</span>
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
