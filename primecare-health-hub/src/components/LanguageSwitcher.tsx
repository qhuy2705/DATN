import { useTranslation } from 'react-i18next';
import { Globe } from 'lucide-react';

export function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const isVi = i18n.language === 'vi';

  return (
    <button
      onClick={() => i18n.changeLanguage(isVi ? 'en' : 'vi')}
      className="flex items-center gap-1.5 px-2 py-1.5 rounded-md hover:bg-muted transition-colors text-sm font-medium text-muted-foreground"
      title={isVi ? 'Switch to English' : 'Chuyển sang Tiếng Việt'}
    >
      <Globe className="h-4 w-4" />
      <span className="hidden sm:inline">{isVi ? 'EN' : 'VI'}</span>
    </button>
  );
}
