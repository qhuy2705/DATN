import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Eye,
  MoreHorizontal,
  Pencil,
  Plus,
  Power,
  PowerOff,
  RotateCcw,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable, type Column } from '@/components/DataTable';
import { PageHeader } from '@/components/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import {
  useCreateRateLimitRule,
  useDisableRateLimitRule,
  useEnableRateLimitRule,
  useRateLimitRule,
  useRateLimitRules,
  useResetRateLimitRuleDefaults,
  useUpdateRateLimitRule,
} from '@/hooks/use-admin-data';
import type {
  CreateRateLimitRuleRequest,
  RateLimitRule,
  UpdateRateLimitRuleRequest,
} from '@/types/api';

type EnabledFilter = 'all' | 'enabled' | 'disabled';

type ConfirmAction =
  | { type: 'enable'; rule: RateLimitRule }
  | { type: 'disable'; rule: RateLimitRule }
  | { type: 'reset'; rule: RateLimitRule };

type EditFormState = {
  limitCount: string;
  windowSeconds: string;
  bucketSeconds: string;
  description: string;
};

type CreateFormState = EditFormState & {
  code: string;
  name: string;
  pathPattern: string;
  httpMethod: string;
  eventType: string;
  enabled: boolean;
  priority: string;
};

type EditFormErrors = Partial<Record<keyof EditFormState, string>>;
type CreateFormErrors = Partial<Record<keyof CreateFormState, string>>;

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const;
const BLOCKED_PATH_PATTERNS = new Set([
  '/',
  '/**',
  '/api',
  '/api/',
  '/api/**',
  '/actuator/**',
  '/ws/**',
]);

function formatDateTime(value: string | undefined, language: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(language.toLowerCase().startsWith('en') ? 'en-US' : 'vi-VN');
}

function parseWholeNumber(value: string) {
  if (!value.trim()) return Number.NaN;
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : Number.NaN;
}

function optionalDescription(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function validateLimitFields(
  form: EditFormState,
  t: TFunction,
): { errors: EditFormErrors; values: Omit<UpdateRateLimitRuleRequest, 'description'> | null } {
  const errors: EditFormErrors = {};
  const limitCount = parseWholeNumber(form.limitCount);
  const windowSeconds = parseWholeNumber(form.windowSeconds);
  const bucketSeconds = parseWholeNumber(form.bucketSeconds);

  if (!Number.isInteger(limitCount) || limitCount < 1 || limitCount > 100000) {
    errors.limitCount = t('modules.rateLimits.validation.limitRange');
  }
  if (!Number.isInteger(windowSeconds) || windowSeconds < 1 || windowSeconds > 86400) {
    errors.windowSeconds = t('modules.rateLimits.validation.windowRange');
  }
  if (!Number.isInteger(bucketSeconds) || bucketSeconds < 1) {
    errors.bucketSeconds = t('modules.rateLimits.validation.bucketMin');
  }
  if (!errors.windowSeconds && !errors.bucketSeconds && bucketSeconds > windowSeconds) {
    errors.bucketSeconds = t('modules.rateLimits.validation.bucketMax');
  }
  if (form.description.trim().length > 1000) {
    errors.description = t('modules.rateLimits.validation.descriptionMax');
  }

  if (Object.keys(errors).length > 0) return { errors, values: null };

  return {
    errors,
    values: {
      limitCount,
      windowSeconds,
      bucketSeconds,
    },
  };
}

function validateEditForm(form: EditFormState, t: TFunction) {
  const validation = validateLimitFields(form, t);
  if (!validation.values) return { errors: validation.errors, body: null };

  return {
    errors: validation.errors,
    body: {
      ...validation.values,
      description: optionalDescription(form.description),
    } satisfies UpdateRateLimitRuleRequest,
  };
}

function validateCreateForm(form: CreateFormState, t: TFunction) {
  const errors: CreateFormErrors = {};
  const code = form.code.trim();
  const name = form.name.trim();
  const pathPattern = form.pathPattern.trim();
  const eventType = form.eventType.trim();
  const priority = parseWholeNumber(form.priority);
  const limitValidation = validateLimitFields(form, t);

  Object.assign(errors, limitValidation.errors);

  if (!code) {
    errors.code = t('modules.rateLimits.validation.codeRequired');
  } else if (!/^[A-Z0-9_]+$/.test(code)) {
    errors.code = t('modules.rateLimits.validation.codePattern');
  } else if (code.length > 128) {
    errors.code = t('modules.rateLimits.validation.codeMax');
  }

  if (!name) {
    errors.name = t('modules.rateLimits.validation.nameRequired');
  } else if (name.length > 255) {
    errors.name = t('modules.rateLimits.validation.nameMax');
  }

  if (!pathPattern) {
    errors.pathPattern = t('modules.rateLimits.validation.pathRequired');
  } else if (BLOCKED_PATH_PATTERNS.has(pathPattern)) {
    errors.pathPattern = t('modules.rateLimits.validation.pathTooBroad');
  } else if (!pathPattern.startsWith('/api/')) {
    errors.pathPattern = t('modules.rateLimits.validation.pathPrefix');
  } else if (/\s/.test(pathPattern)) {
    errors.pathPattern = t('modules.rateLimits.validation.pathSpaces');
  }

  if (!HTTP_METHODS.includes(form.httpMethod as (typeof HTTP_METHODS)[number])) {
    errors.httpMethod = t('modules.rateLimits.validation.methodInvalid');
  }

  if (!eventType) {
    errors.eventType = t('modules.rateLimits.validation.eventTypeRequired');
  } else if (!/^[A-Z0-9_]+$/.test(eventType)) {
    errors.eventType = t('modules.rateLimits.validation.eventTypePattern');
  } else if (eventType.length > 64) {
    errors.eventType = t('modules.rateLimits.validation.eventTypeMax');
  }

  if (!Number.isInteger(priority) || priority < 1 || priority > 100000) {
    errors.priority = t('modules.rateLimits.validation.priorityRange');
  }

  if (Object.keys(errors).length > 0 || !limitValidation.values) {
    return { errors, body: null };
  }

  return {
    errors,
    body: {
      ...limitValidation.values,
      code,
      name,
      description: optionalDescription(form.description),
      pathPattern,
      httpMethod: form.httpMethod,
      eventType,
      enabled: form.enabled,
      priority,
    } satisfies CreateRateLimitRuleRequest,
  };
}

function buildEditForm(rule?: RateLimitRule | null): EditFormState {
  return {
    limitCount: rule ? String(rule.limitCount) : '',
    windowSeconds: rule ? String(rule.windowSeconds) : '',
    bucketSeconds: rule ? String(rule.bucketSeconds) : '',
    description: rule?.description ?? '',
  };
}

function buildCreateForm(): CreateFormState {
  return {
    code: '',
    name: '',
    description: '',
    pathPattern: '',
    httpMethod: 'GET',
    eventType: '',
    limitCount: '',
    windowSeconds: '',
    bucketSeconds: '',
    enabled: true,
    priority: '',
  };
}

function StatusBadge({ enabled }: { enabled: boolean }) {
  const { t } = useTranslation();
  return (
    <Badge variant={enabled ? 'success' : 'secondary'}>
      {enabled ? t('modules.rateLimits.enabled') : t('modules.rateLimits.disabled')}
    </Badge>
  );
}

function MethodBadge({ method }: { method: string }) {
  return <Badge variant="outline">{method || '-'}</Badge>;
}

function ReadonlyField({
  label,
  value,
  description,
  wide,
}: {
  label: string;
  value?: ReactNode;
  description?: string;
  wide?: boolean;
}) {
  const displayValue =
    value === null || typeof value === 'undefined' || value === '' ? '-' : value;

  return (
    <div className={wide ? 'min-w-0 md:col-span-2' : 'min-w-0'}>
      <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <p className="mt-1 break-words text-sm text-foreground">{displayValue}</p>
      {description ? (
        <p className="mt-1 text-xs leading-5 text-muted-foreground">{description}</p>
      ) : null}
    </div>
  );
}

function FieldError({ message }: { message?: string }) {
  return message ? <p className="text-xs text-destructive">{message}</p> : null;
}

function FieldHint({ children }: { children: ReactNode }) {
  return <p className="text-xs leading-5 text-muted-foreground">{children}</p>;
}

function DialogSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      {children}
    </section>
  );
}

function RateLimitActions({
  rule,
  onView,
  onEdit,
  onConfirm,
}: {
  rule: RateLimitRule;
  onView: () => void;
  onEdit: () => void;
  onConfirm: (action: ConfirmAction) => void;
}) {
  const { t } = useTranslation();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" aria-label={t('common.actions')}>
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuItem onClick={onView}>
          <Eye className="mr-2 h-4 w-4" />
          {t('modules.rateLimits.actions.viewDetails')}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={onEdit}>
          <Pencil className="mr-2 h-4 w-4" />
          {t('modules.rateLimits.actions.edit')}
        </DropdownMenuItem>
        <DropdownMenuItem
          onClick={() => onConfirm({ type: rule.enabled ? 'disable' : 'enable', rule })}
        >
          {rule.enabled ? (
            <PowerOff className="mr-2 h-4 w-4" />
          ) : (
            <Power className="mr-2 h-4 w-4" />
          )}
          {rule.enabled
            ? t('modules.rateLimits.actions.disable')
            : t('modules.rateLimits.actions.enable')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => onConfirm({ type: 'reset', rule })}>
          <RotateCcw className="mr-2 h-4 w-4" />
          {t('modules.rateLimits.actions.reset')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function RateLimitDetailsDialog({
  open,
  rule,
  isFetching,
  isError,
  onOpenChange,
}: {
  open: boolean;
  rule: RateLimitRule | null;
  isFetching: boolean;
  isError: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, i18n } = useTranslation();
  const enabledLabel = rule?.enabled
    ? t('modules.rateLimits.enabled')
    : t('modules.rateLimits.disabled');
  const defaultEnabledLabel = rule?.defaultEnabled
    ? t('modules.rateLimits.enabled')
    : t('modules.rateLimits.disabled');

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('modules.rateLimits.dialogs.detailsTitle')}</DialogTitle>
          <DialogDescription>{t('modules.rateLimits.dialogs.detailsDesc')}</DialogDescription>
        </DialogHeader>

        {rule ? (
          <div className="space-y-4">
            {isError ? (
              <p className="rounded-md border border-destructive/20 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {t('modules.rateLimits.detailError')}
              </p>
            ) : isFetching ? (
              <p className="text-xs text-muted-foreground">
                {t('modules.rateLimits.refreshingDetails')}
              </p>
            ) : null}

            <div className="grid gap-3 rounded-lg border border-border/70 bg-muted/20 p-4 md:grid-cols-2">
              <ReadonlyField label={t('modules.rateLimits.fields.id')} value={rule.id} />
              <ReadonlyField label={t('modules.rateLimits.fields.code')} value={rule.code} />
              <ReadonlyField label={t('modules.rateLimits.fields.name')} value={rule.name} />
              <ReadonlyField label={t('modules.rateLimits.fields.enabled')} value={enabledLabel} />
              <ReadonlyField
                label={t('modules.rateLimits.fields.description')}
                value={rule.description}
                wide
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.pathPattern')}
                value={rule.pathPattern}
                wide
              />
              <ReadonlyField label={t('modules.rateLimits.fields.httpMethod')} value={rule.httpMethod} />
              <ReadonlyField
                label={t('modules.rateLimits.fields.eventType')}
                value={rule.eventType}
                description={t('modules.rateLimits.help.detailEventType')}
              />
              <ReadonlyField label={t('modules.rateLimits.fields.limitCount')} value={rule.limitCount} />
              <ReadonlyField label={t('modules.rateLimits.fields.windowSeconds')} value={rule.windowSeconds} />
              <ReadonlyField
                label={t('modules.rateLimits.fields.bucketSeconds')}
                value={rule.bucketSeconds}
                description={t('modules.rateLimits.help.detailBucket')}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.priorityDetail')}
                value={rule.priority}
                description={t('modules.rateLimits.help.detailPriority')}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultLimitCount')}
                value={rule.defaultLimitCount}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultWindowSeconds')}
                value={rule.defaultWindowSeconds}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultBucketSeconds')}
                value={rule.defaultBucketSeconds}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultEnabled')}
                value={defaultEnabledLabel}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.createdAt')}
                value={formatDateTime(rule.createdAt, i18n.language)}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.updatedAt')}
                value={formatDateTime(rule.updatedAt, i18n.language)}
              />
              <ReadonlyField label={t('modules.rateLimits.fields.updatedBy')} value={rule.updatedBy} />
            </div>
          </div>
        ) : (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('modules.rateLimits.noRuleSelected')}
          </p>
        )}
      </DialogContent>
    </Dialog>
  );
}

function EditableLimitFields({
  form,
  errors,
  includeDescription = true,
  bucketHint,
  onChange,
}: {
  form: EditFormState;
  errors: EditFormErrors;
  includeDescription?: boolean;
  bucketHint?: string;
  onChange: <Key extends keyof EditFormState>(key: Key, value: EditFormState[Key]) => void;
}) {
  const { t } = useTranslation();
  const bucketHelper = bucketHint ?? t('modules.rateLimits.help.editBucket');

  return (
    <>
      <div className="grid gap-4 md:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="rate-limit-count">{t('modules.rateLimits.fields.limitCount')}</Label>
          <Input
            id="rate-limit-count"
            type="number"
            min={1}
            max={100000}
            step={1}
            value={form.limitCount}
            aria-invalid={Boolean(errors.limitCount)}
            onChange={(event) => onChange('limitCount', event.target.value)}
          />
          <FieldError message={errors.limitCount} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="rate-limit-window">{t('modules.rateLimits.fields.windowSeconds')}</Label>
          <Input
            id="rate-limit-window"
            type="number"
            min={1}
            max={86400}
            step={1}
            value={form.windowSeconds}
            aria-invalid={Boolean(errors.windowSeconds)}
            onChange={(event) => onChange('windowSeconds', event.target.value)}
          />
          <FieldError message={errors.windowSeconds} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="rate-limit-bucket">{t('modules.rateLimits.fields.bucketSeconds')}</Label>
          <Input
            id="rate-limit-bucket"
            type="number"
            min={1}
            step={1}
            value={form.bucketSeconds}
            aria-invalid={Boolean(errors.bucketSeconds)}
            onChange={(event) => onChange('bucketSeconds', event.target.value)}
          />
          <FieldError message={errors.bucketSeconds} />
          <FieldHint>{bucketHelper}</FieldHint>
        </div>
      </div>

      {includeDescription ? (
        <div className="space-y-1.5">
          <Label htmlFor="rate-limit-description">{t('modules.rateLimits.fields.description')}</Label>
          <Textarea
            id="rate-limit-description"
            rows={3}
            value={form.description}
            aria-invalid={Boolean(errors.description)}
            onChange={(event) => onChange('description', event.target.value)}
          />
          <FieldError message={errors.description} />
        </div>
      ) : null}
    </>
  );
}

function RateLimitEditDialog({
  open,
  rule,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  rule: RateLimitRule | null;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (body: UpdateRateLimitRuleRequest) => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<EditFormState>(() => buildEditForm(rule));
  const [errors, setErrors] = useState<EditFormErrors>({});

  useEffect(() => {
    if (!open) return;
    setForm(buildEditForm(rule));
    setErrors({});
  }, [open, rule]);

  const updateField = <Key extends keyof EditFormState>(
    key: Key,
    value: EditFormState[Key],
  ) => {
    setForm((current) => ({ ...current, [key]: value }));
    if (key in errors) {
      setErrors((current) => ({ ...current, [key]: undefined }));
    }
  };

  const handleSubmit = () => {
    const validation = validateEditForm(form, t);
    setErrors(validation.errors);
    if (!validation.body) return;
    onSubmit(validation.body);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('modules.rateLimits.dialogs.editTitle')}</DialogTitle>
          <DialogDescription>{t('modules.rateLimits.dialogs.editDesc')}</DialogDescription>
        </DialogHeader>

        {rule ? (
          <div className="space-y-4">
            <div className="grid gap-3 rounded-lg border border-border/70 bg-muted/20 p-4 md:grid-cols-2">
              <ReadonlyField label={t('modules.rateLimits.fields.code')} value={rule.code} />
              <ReadonlyField label={t('modules.rateLimits.fields.name')} value={rule.name} />
              <ReadonlyField
                label={t('modules.rateLimits.fields.enabled')}
                value={rule.enabled ? t('modules.rateLimits.enabled') : t('modules.rateLimits.disabled')}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.pathPattern')}
                value={rule.pathPattern}
                wide
              />
              <ReadonlyField label={t('modules.rateLimits.fields.httpMethod')} value={rule.httpMethod} />
              <ReadonlyField label={t('modules.rateLimits.fields.eventType')} value={rule.eventType} />
              <ReadonlyField label={t('modules.rateLimits.fields.priority')} value={rule.priority} />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultLimitCount')}
                value={rule.defaultLimitCount}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultWindowSeconds')}
                value={rule.defaultWindowSeconds}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultBucketSeconds')}
                value={rule.defaultBucketSeconds}
              />
              <ReadonlyField
                label={t('modules.rateLimits.fields.defaultEnabled')}
                value={rule.defaultEnabled ? t('modules.rateLimits.enabled') : t('modules.rateLimits.disabled')}
              />
            </div>

            <EditableLimitFields form={form} errors={errors} onChange={updateField} />
          </div>
        ) : (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('modules.rateLimits.noRuleSelected')}
          </p>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            {t('modules.rateLimits.dialogs.cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={!rule || isPending}>
            {isPending
              ? t('modules.rateLimits.dialogs.saving')
              : t('modules.rateLimits.dialogs.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RateLimitCreateDialog({
  open,
  isPending,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  isPending: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (body: CreateRateLimitRuleRequest) => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<CreateFormState>(() => buildCreateForm());
  const [errors, setErrors] = useState<CreateFormErrors>({});

  useEffect(() => {
    if (!open) return;
    setForm(buildCreateForm());
    setErrors({});
  }, [open]);

  const updateField = <Key extends keyof CreateFormState>(
    key: Key,
    value: CreateFormState[Key],
  ) => {
    setForm((current) => ({ ...current, [key]: value }));
    if (key in errors) {
      setErrors((current) => ({ ...current, [key]: undefined }));
    }
  };

  const handleSubmit = () => {
    const validation = validateCreateForm(form, t);
    setErrors(validation.errors);
    if (!validation.body) return;
    onSubmit(validation.body);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('modules.rateLimits.dialogs.createTitle')}</DialogTitle>
          <DialogDescription>{t('modules.rateLimits.dialogs.createDesc')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="rounded-lg border border-warning/30 bg-warning/10 p-3 text-sm text-warning">
            {t('modules.rateLimits.dialogs.createWarning')}
          </div>

          <DialogSection title={t('modules.rateLimits.dialogs.sectionRuleInfo')}>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="rate-limit-create-code">{t('modules.rateLimits.fields.code')}</Label>
                <Input
                  id="rate-limit-create-code"
                  value={form.code}
                  aria-invalid={Boolean(errors.code)}
                  onChange={(event) => updateField('code', event.target.value)}
                />
                <FieldHint>{t('modules.rateLimits.help.code')}</FieldHint>
                <FieldError message={errors.code} />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="rate-limit-create-name">{t('modules.rateLimits.fields.name')}</Label>
                <Input
                  id="rate-limit-create-name"
                  value={form.name}
                  aria-invalid={Boolean(errors.name)}
                  onChange={(event) => updateField('name', event.target.value)}
                />
                <FieldError message={errors.name} />
              </div>

              <div className="space-y-1.5 md:col-span-2">
                <Label htmlFor="rate-limit-create-description">
                  {t('modules.rateLimits.fields.description')}
                </Label>
                <Textarea
                  id="rate-limit-create-description"
                  rows={3}
                  value={form.description}
                  aria-invalid={Boolean(errors.description)}
                  onChange={(event) => updateField('description', event.target.value)}
                />
                <FieldError message={errors.description} />
              </div>
            </div>
          </DialogSection>

          <DialogSection title={t('modules.rateLimits.dialogs.sectionApi')}>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1.5">
                <Label>{t('modules.rateLimits.fields.httpMethod')}</Label>
                <Select
                  value={form.httpMethod}
                  onValueChange={(value) => updateField('httpMethod', value)}
                >
                  <SelectTrigger aria-invalid={Boolean(errors.httpMethod)}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {HTTP_METHODS.map((method) => (
                      <SelectItem key={method} value={method}>
                        {method}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldError message={errors.httpMethod} />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="rate-limit-create-path">{t('modules.rateLimits.fields.pathPattern')}</Label>
                <Input
                  id="rate-limit-create-path"
                  value={form.pathPattern}
                  aria-invalid={Boolean(errors.pathPattern)}
                  onChange={(event) => updateField('pathPattern', event.target.value)}
                />
                <FieldHint>{t('modules.rateLimits.help.pathPattern')}</FieldHint>
                <FieldError message={errors.pathPattern} />
              </div>
            </div>
          </DialogSection>

          <DialogSection title={t('modules.rateLimits.dialogs.sectionLimit')}>
            <EditableLimitFields
              form={form}
              errors={errors}
              includeDescription={false}
              bucketHint={t('modules.rateLimits.help.bucket')}
              onChange={updateField}
            />

            <div className="flex items-center justify-between gap-3 rounded-lg border border-border/70 px-3 py-2.5">
              <div>
                <Label htmlFor="rate-limit-create-enabled">
                  {t('modules.rateLimits.fields.enabledOnCreate')}
                </Label>
                <FieldHint>{t('modules.rateLimits.dialogs.createDesc')}</FieldHint>
              </div>
              <Switch
                id="rate-limit-create-enabled"
                checked={form.enabled}
                onCheckedChange={(checked) => updateField('enabled', checked)}
              />
            </div>
          </DialogSection>

          <DialogSection title={t('modules.rateLimits.dialogs.sectionTechnical')}>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="rate-limit-create-event">{t('modules.rateLimits.fields.eventType')}</Label>
                <Input
                  id="rate-limit-create-event"
                  value={form.eventType}
                  aria-invalid={Boolean(errors.eventType)}
                  onChange={(event) => updateField('eventType', event.target.value)}
                />
                <FieldHint>{t('modules.rateLimits.help.eventType')}</FieldHint>
                <FieldError message={errors.eventType} />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="rate-limit-create-priority">{t('modules.rateLimits.fields.priority')}</Label>
                <Input
                  id="rate-limit-create-priority"
                  type="number"
                  min={1}
                  max={100000}
                  step={1}
                  value={form.priority}
                  aria-invalid={Boolean(errors.priority)}
                  onChange={(event) => updateField('priority', event.target.value)}
                />
                <FieldHint>{t('modules.rateLimits.help.priority')}</FieldHint>
                <FieldError message={errors.priority} />
              </div>
            </div>
          </DialogSection>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            {t('modules.rateLimits.dialogs.cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={isPending}>
            {isPending
              ? t('modules.rateLimits.dialogs.creating')
              : t('modules.rateLimits.dialogs.create')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function getConfirmCopy(action: ConfirmAction | null, t: TFunction) {
  if (!action) {
    return {
      title: t('modules.rateLimits.confirm.emptyTitle'),
      description: '',
      confirmLabel: t('common.confirm'),
    };
  }

  if (action.type === 'enable') {
    return {
      title: t('modules.rateLimits.confirm.enableTitle'),
      description: t('modules.rateLimits.confirm.enableDesc', { code: action.rule.code }),
      confirmLabel: t('modules.rateLimits.confirm.enableLabel'),
    };
  }

  if (action.type === 'disable') {
    return {
      title: t('modules.rateLimits.confirm.disableTitle'),
      description: t('modules.rateLimits.confirm.disableDesc', { code: action.rule.code }),
      confirmLabel: t('modules.rateLimits.confirm.disableLabel'),
    };
  }

  return {
    title: t('modules.rateLimits.confirm.resetTitle'),
    description: t('modules.rateLimits.confirm.resetDesc'),
    confirmLabel: t('modules.rateLimits.confirm.resetLabel'),
  };
}

export default function RateLimitsPage() {
  const { t } = useTranslation();
  const [keyword, setKeyword] = useState('');
  const [enabledFilter, setEnabledFilter] = useState<EnabledFilter>('all');
  const [viewRule, setViewRule] = useState<RateLimitRule | null>(null);
  const [editRule, setEditRule] = useState<RateLimitRule | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);

  const rulesQuery = useRateLimitRules();
  const detailQuery = useRateLimitRule(viewRule?.id, { enabled: Boolean(viewRule?.id) });
  const createMutation = useCreateRateLimitRule();
  const updateMutation = useUpdateRateLimitRule();
  const enableMutation = useEnableRateLimitRule();
  const disableMutation = useDisableRateLimitRule();
  const resetMutation = useResetRateLimitRuleDefaults();

  const detailRule =
    viewRule && detailQuery.data?.id === viewRule.id ? detailQuery.data : viewRule;

  const filteredRules = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();

    return (rulesQuery.data ?? []).filter((rule) => {
      const matchesEnabled =
        enabledFilter === 'all' ||
        (enabledFilter === 'enabled' && rule.enabled) ||
        (enabledFilter === 'disabled' && !rule.enabled);

      if (!matchesEnabled) return false;
      if (!normalizedKeyword) return true;

      return [rule.code, rule.name, rule.pathPattern, rule.eventType].some((value) =>
        String(value ?? '').toLowerCase().includes(normalizedKeyword),
      );
    });
  }, [enabledFilter, keyword, rulesQuery.data]);

  const isActionPending =
    enableMutation.isPending || disableMutation.isPending || resetMutation.isPending;

  const handleCreateSubmit = async (body: CreateRateLimitRuleRequest) => {
    try {
      await createMutation.mutateAsync(body);
      setCreateOpen(false);
      await rulesQuery.refetch();
    } catch {
      // Mutation hook shows backend error.
    }
  };

  const handleEditSubmit = async (body: UpdateRateLimitRuleRequest) => {
    if (!editRule) return;

    try {
      await updateMutation.mutateAsync({ id: editRule.id, body });
      setEditRule(null);
      await rulesQuery.refetch();
      if (viewRule?.id === editRule.id) {
        await detailQuery.refetch();
      }
    } catch {
      // Mutation hook shows backend error.
    }
  };

  const handleConfirmAction = async () => {
    if (!confirmAction) return;

    try {
      if (confirmAction.type === 'enable') {
        await enableMutation.mutateAsync(confirmAction.rule.id);
      } else if (confirmAction.type === 'disable') {
        await disableMutation.mutateAsync(confirmAction.rule.id);
      } else {
        await resetMutation.mutateAsync(confirmAction.rule.id);
      }

      setConfirmAction(null);
      await rulesQuery.refetch();
      if (viewRule?.id) {
        await detailQuery.refetch();
      }
    } catch {
      // Mutation hooks show backend errors.
    }
  };

  const columns: Column<RateLimitRule>[] = [
    {
      key: 'rule',
      header: t('modules.rateLimits.table.rule'),
      className: 'min-w-56',
      cell: (rule) => (
        <div className="min-w-0">
          <p className="font-medium text-foreground">{rule.name || '-'}</p>
          <p className="mt-1 truncate font-mono text-xs text-muted-foreground">{rule.code || '-'}</p>
        </div>
      ),
    },
    {
      key: 'endpoint',
      header: t('modules.rateLimits.table.endpoint'),
      className: 'min-w-72',
      cell: (rule) => (
        <div className="flex min-w-0 flex-col gap-1">
          <div>
            <MethodBadge method={rule.httpMethod} />
          </div>
          <span className="break-all font-mono text-xs text-foreground">
            {rule.pathPattern || '-'}
          </span>
        </div>
      ),
    },
    {
      key: 'limit',
      header: t('modules.rateLimits.table.limit'),
      className: 'min-w-40',
      cell: (rule) => (
        <span className="font-medium tabular-nums">
          {t('modules.rateLimits.table.limitWindow', {
            limit: rule.limitCount,
            window: rule.windowSeconds,
          })}
        </span>
      ),
    },
    {
      key: 'enabled',
      header: t('modules.rateLimits.table.status'),
      cell: (rule) => <StatusBadge enabled={rule.enabled} />,
    },
  ];

  const confirmCopy = getConfirmCopy(confirmAction, t);

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('modules.rateLimits.title')}
        description={t('modules.rateLimits.desc')}
        actions={
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            {t('modules.rateLimits.addRule')}
          </Button>
        }
      />

      <Card className="border-primary/20 bg-primary/5 p-3 text-sm leading-6 text-muted-foreground">
        {t('modules.rateLimits.helper')}
      </Card>

      <DataTable
        columns={columns}
        data={filteredRules}
        searchValue={keyword}
        onSearchChange={setKeyword}
        searchPlaceholder={t('modules.rateLimits.searchPlaceholder')}
        toolbar={
          <Select
            value={enabledFilter}
            onValueChange={(value) => setEnabledFilter(value as EnabledFilter)}
          >
            <SelectTrigger className="w-full sm:w-44">
              <SelectValue placeholder={t('modules.rateLimits.enabledFilter')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('modules.rateLimits.allStatuses')}</SelectItem>
              <SelectItem value="enabled">{t('modules.rateLimits.enabled')}</SelectItem>
              <SelectItem value="disabled">{t('modules.rateLimits.disabled')}</SelectItem>
            </SelectContent>
          </Select>
        }
        isLoading={rulesQuery.isLoading}
        isError={rulesQuery.isError}
        errorMessage={t('modules.rateLimits.loadError')}
        onRetry={() => void rulesQuery.refetch()}
        emptyMessage={t('modules.rateLimits.noData')}
        keyExtractor={(rule) => rule.id}
        actions={(rule) => (
          <RateLimitActions
            rule={rule}
            onView={() => setViewRule(rule)}
            onEdit={() => setEditRule(rule)}
            onConfirm={setConfirmAction}
          />
        )}
      />

      <RateLimitDetailsDialog
        open={Boolean(viewRule)}
        rule={detailRule}
        isFetching={detailQuery.isFetching}
        isError={detailQuery.isError}
        onOpenChange={(open) => {
          if (!open) setViewRule(null);
        }}
      />

      <RateLimitCreateDialog
        open={createOpen}
        isPending={createMutation.isPending}
        onOpenChange={(open) => {
          if (!open && !createMutation.isPending) setCreateOpen(false);
          if (open) setCreateOpen(true);
        }}
        onSubmit={(body) => void handleCreateSubmit(body)}
      />

      <RateLimitEditDialog
        open={Boolean(editRule)}
        rule={editRule}
        isPending={updateMutation.isPending}
        onOpenChange={(open) => {
          if (!open && !updateMutation.isPending) setEditRule(null);
        }}
        onSubmit={(body) => void handleEditSubmit(body)}
      />

      <ConfirmDialog
        open={Boolean(confirmAction)}
        onOpenChange={(open) => {
          if (!open && !isActionPending) setConfirmAction(null);
        }}
        title={confirmCopy.title}
        description={confirmCopy.description}
        confirmLabel={confirmCopy.confirmLabel}
        variant={confirmAction?.type === 'disable' ? 'destructive' : 'default'}
        loading={isActionPending}
        onConfirm={() => void handleConfirmAction()}
      />
    </div>
  );
}
