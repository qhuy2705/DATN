import { useState } from 'react';
import { DataTable, type Column } from '@/components/DataTable';
import { FilterBar } from '@/components/FilterBar';
import { PageHeader } from '@/components/PageHeader';
import { useAuditLogs } from '@/hooks/use-admin-data';
import type { AuditLog } from '@/types/api';

const actionOptions = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'LOGIN',
  'LOGOUT',
  'CHECK_IN',
  'CONFIRM',
  'CANCEL',
].map((value) => ({ label: value, value }));

export default function AuditLogsPage() {
  const [page, setPage] = useState(1);
  const [actionFilter, setActionFilter] = useState('__all__');

  const { data, isLoading } = useAuditLogs({
    page: String(page - 1),
    size: '20',
    ...(actionFilter !== '__all__' ? { action: actionFilter } : {}),
  });

  const columns: Column<AuditLog>[] = [
    {
      key: 'createdAt',
      header: 'Thời gian',
      cell: (row) => (
        <div>
          <p className="font-medium">{row.createdAt ? new Date(row.createdAt).toLocaleString('vi-VN') : '-'}</p>
        </div>
      ),
    },
    {
      key: 'actorName',
      header: 'Người thực hiện',
      cell: (row) => (
        <div>
          <p className="font-medium">{row.actorName || row.actorId || '-'}</p>
          <p className="text-xs text-muted-foreground">{row.actorRole || '-'}</p>
        </div>
      ),
    },
    {
      key: 'action',
      header: 'Hành động',
      cell: (row) => <span className="font-medium">{row.action}</span>,
    },
    {
      key: 'targetType',
      header: 'Đối tượng',
      cell: (row) => (
        <div>
          <p>{row.targetType || '-'}</p>
          <p className="text-xs text-muted-foreground">{row.targetId || '-'}</p>
        </div>
      ),
    },
    {
      key: 'metadata',
      header: 'Chi tiết',
      cell: (row) => (
        <div className="max-w-[380px] truncate text-sm text-muted-foreground">
          {row.description || (row.metadata ? JSON.stringify(row.metadata) : '-')}
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="Audit Logs" description="Lịch sử thao tác quản trị" />
      <FilterBar
        filters={[
          {
            key: 'action',
            label: 'Hành động',
            options: actionOptions,
            value: actionFilter,
            onChange: setActionFilter,
          },
        ]}
      />
      <DataTable
        columns={columns}
        data={data?.items ?? []}
        page={page}
        totalPages={Math.max(data?.meta.totalPages ?? 1, 1)}
        onPageChange={setPage}
        emptyMessage={isLoading ? 'Đang tải...' : 'Không có dữ liệu'}
        keyExtractor={(row) => row.id}
      />
    </div>
  );
}