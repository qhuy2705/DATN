import type {
  ChronicCondition,
  ConfidenceLevel,
  FunctionalImpact,
  PreTriageLevel,
  PreTriageSource,
  RedFlag,
  SymptomOnset,
  TriagePriority,
} from '@/types/api';

type PriorityLike = TriagePriority | 'P1' | 'P2' | 'P3' | 'EMERGENCY' | 'HIGH' | 'NORMAL' | string | null | undefined;

export const TRIAGE_PRIORITY_OPTIONS: Array<{
  value: TriagePriority;
  label: string;
  description: string;
}> = [
  { value: 'URGENT', label: 'P1', description: 'Ưu tiên cao' },
  { value: 'PRIORITY', label: 'P2', description: 'Cần ưu tiên' },
  { value: 'ROUTINE', label: 'P3', description: 'Thông thường' },
];

export const SYMPTOM_ONSET_LABELS: Record<SymptomOnset, string> = {
  TODAY: 'Hôm nay / đột ngột',
  DAYS_2_3: '2-3 ngày gần đây',
  WEEK_1: 'Khoảng 1 tuần',
  OVER_MONTH: 'Trên 1 tháng',
  UNKNOWN: 'Không rõ',
};

export const CHRONIC_CONDITION_LABELS: Record<ChronicCondition, string> = {
  CARDIOVASCULAR: 'Tim mạch / huyết áp',
  DIABETES: 'Tiểu đường',
  RESPIRATORY: 'Hô hấp / hen / COPD',
  CANCER: 'Ung thư',
  IMMUNODEFICIENCY: 'Suy giảm miễn dịch',
  PREGNANCY: 'Đang mang thai',
  ELDERLY: 'Người cao tuổi',
  NONE: 'Không có',
};

export const FUNCTIONAL_IMPACT_LABELS: Record<FunctionalImpact, string> = {
  NORMAL: 'Vẫn sinh hoạt bình thường',
  MILD_DIFFICULTY: 'Hơi khó chịu',
  SEVERE_DIFFICULTY: 'Rất khó khăn',
  UNABLE_SELF_CARE: 'Không tự sinh hoạt được',
  UNKNOWN: 'Không rõ',
};

export const RED_FLAG_LABELS: Record<RedFlag, string> = {
  CHEST_PAIN: 'Đau ngực',
  DYSPNEA: 'Khó thở',
  FAINTING: 'Ngất / gần ngất',
  SEIZURE: 'Co giật',
  STROKE_SIGNS: 'Yếu liệt nửa người / nói khó / méo miệng',
  HEAVY_BLEEDING: 'Chảy máu nhiều',
  SEVERE_PAIN: 'Đau dữ dội',
  HIGH_FEVER: 'Sốt cao',
  ALLERGIC_REACTION: 'Dị ứng nặng / sưng mặt / nổi mề đay',
  NONE: 'Không có dấu hiệu trên',
};

export const SYMPTOM_ONSET_OPTIONS = Object.entries(SYMPTOM_ONSET_LABELS).map(([value, label]) => ({
  value: value as SymptomOnset,
  label,
}));

export const CHRONIC_CONDITION_OPTIONS = Object.entries(CHRONIC_CONDITION_LABELS).map(
  ([value, label]) => ({
    value: value as ChronicCondition,
    label,
  }),
);

export const FUNCTIONAL_IMPACT_OPTIONS = Object.entries(FUNCTIONAL_IMPACT_LABELS).map(
  ([value, label]) => ({
    value: value as FunctionalImpact,
    label,
  }),
);

export const RED_FLAG_OPTIONS = Object.entries(RED_FLAG_LABELS).map(([value, label]) => ({
  value: value as RedFlag,
  label,
}));

export function normalizePriority(priority: PriorityLike): TriagePriority | null {
  const normalized = typeof priority === 'string' ? priority.trim().toUpperCase() : '';

  switch (normalized) {
    case 'URGENT':
    case 'P1':
    case 'EMERGENCY':
      return 'URGENT';
    case 'PRIORITY':
    case 'P2':
    case 'HIGH':
      return 'PRIORITY';
    case 'ROUTINE':
    case 'P3':
    case 'NORMAL':
      return 'ROUTINE';
    default:
      return null;
  }
}

export function getPriorityDisplay(priority: PriorityLike) {
  switch (normalizePriority(priority)) {
    case 'URGENT':
      return { label: 'P1', description: 'Ưu tiên cao' };
    case 'PRIORITY':
      return { label: 'P2', description: 'Cần ưu tiên' };
    case 'ROUTINE':
      return { label: 'P3', description: 'Thông thường' };
    default:
      return { label: 'Chưa phân loại', description: '' };
  }
}

export function getPriorityClass(priority: PriorityLike) {
  switch (normalizePriority(priority)) {
    case 'URGENT':
      return 'border-destructive/25 bg-destructive/10 text-destructive';
    case 'PRIORITY':
      return 'border-warning/25 bg-warning/10 text-warning';
    case 'ROUTINE':
      return 'border-success/20 bg-success/10 text-success';
    default:
      return 'border-border bg-muted text-muted-foreground';
  }
}

export const getPriorityBadgeClass = getPriorityClass;

export function getPreTriageLevelDisplay(level?: PreTriageLevel | string | null) {
  switch (level) {
    case 'RED_FLAG':
      return 'Cảnh báo cần xác minh';
    case 'WATCH':
      return 'Cần chú ý';
    case 'NONE':
      return 'Thông thường';
    default:
      return 'Chưa có sàng lọc';
  }
}

export function getPreTriageLevelClass(level?: PreTriageLevel | string | null) {
  switch (level) {
    case 'RED_FLAG':
      return 'border-destructive/25 bg-destructive/10 text-destructive';
    case 'WATCH':
      return 'border-warning/25 bg-warning/10 text-warning';
    case 'NONE':
      return 'border-success/20 bg-success/10 text-success';
    default:
      return 'border-border bg-muted text-muted-foreground';
  }
}

export function getSourceDisplay(source?: PreTriageSource | string | null) {
  switch (source) {
    case 'RULE':
      return { label: 'Rule-based', description: 'Knowledge base và rule cố định' };
    case 'AI':
      return { label: 'AI đọc mô tả tự do', description: 'AI hỗ trợ sàng lọc sơ bộ từ mô tả tự do' };
    case 'HYBRID':
      return { label: 'Card + Knowledge base + AI', description: 'Card + Knowledge base + AI đọc mô tả tự do' };
    default:
      return { label: 'Không rõ', description: '' };
  }
}

export function getConfidenceDisplay(level?: ConfidenceLevel | string | null) {
  switch (level) {
    case 'HIGH':
      return 'Cao';
    case 'MEDIUM':
      return 'Trung bình';
    case 'LOW':
      return 'Thấp';
    default:
      return 'Chưa đánh giá';
  }
}

export function getConfidenceClass(level?: ConfidenceLevel | string | null) {
  switch (level) {
    case 'HIGH':
      return 'border-success/20 bg-success/10 text-success';
    case 'MEDIUM':
      return 'border-warning/20 bg-warning/10 text-warning';
    case 'LOW':
      return 'border-border bg-muted text-muted-foreground';
    default:
      return 'border-border bg-muted text-muted-foreground';
  }
}

export function formatTriagePriority(priority: PriorityLike) {
  const display = getPriorityDisplay(priority);
  return display.description ? `${display.label} - ${display.description}` : display.label;
}

export function formatTriageSelection<T extends string>(
  value: T | null | undefined,
  labels: Record<T, string>,
) {
  return value ? labels[value] ?? value : 'Không rõ';
}

export function formatTriageSelections<T extends string>(
  values: T[] | null | undefined,
  labels: Record<T, string>,
) {
  if (!values?.length) return 'Không có';
  return values.map((value) => labels[value] ?? value).join(', ');
}
