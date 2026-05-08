import { Link } from 'react-router-dom';
import { Activity, Mail, MapPin, Phone } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export function PublicFooter() {
  const { t } = useTranslation();

  const quickLinks = [
    { label: t('footer.quickLinks.specialties'), href: '/specialties' },
    { label: t('footer.quickLinks.doctors'), href: '/doctors' },
    { label: t('footer.quickLinks.services'), href: '/medical-services' },
    { label: t('footer.quickLinks.booking'), href: '/booking' },
    { label: t('footer.quickLinks.availability'), href: '/availability' },
  ];

  const supportLinks = [
    { label: t('nav.lookup', { defaultValue: 'Tra cứu lịch & kết quả' }), href: '/appointments/lookup' },
    { label: t('footer.support.about'), href: '/about' },
    { label: t('footer.support.bookingGuide'), href: '/faq' },
    { label: t('footer.support.contact'), href: '/contact' },
  ];

  return (
    <footer className="border-t border-border bg-muted/30">
      <div className="container-page grid gap-10 py-12 md:grid-cols-4">
        <div className="space-y-3">
          <Link to="/" className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl gradient-hero shadow-elevated">
              <Activity className="h-5 w-5 text-primary-foreground" strokeWidth={2.5} />
            </div>
            <span className="text-lg font-bold tracking-tight text-foreground">PrimeCare</span>
          </Link>
          <p className="max-w-xs text-sm leading-6 text-muted-foreground">{t('footer.brandDescription')}</p>
        </div>

        <div>
          <h4 className="mb-3 text-sm font-semibold text-foreground">{t('footer.sections.explore')}</h4>
          <ul className="space-y-2 text-sm text-muted-foreground">
            {quickLinks.map((link) => (
              <li key={link.href}>
                <Link to={link.href} className="transition-colors hover:text-primary">
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h4 className="mb-3 text-sm font-semibold text-foreground">{t('footer.sections.support')}</h4>
          <ul className="space-y-2 text-sm text-muted-foreground">
            {supportLinks.map((link) => (
              <li key={link.href}>
                <Link to={link.href} className="transition-colors hover:text-primary">
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h4 className="mb-3 text-sm font-semibold text-foreground">{t('footer.sections.contact')}</h4>
          <ul className="space-y-2 text-sm text-muted-foreground">
            <li className="flex items-start gap-2">
              <MapPin className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{t('footer.contact.address')}</span>
            </li>
            <li className="flex items-center gap-2">
              <Phone className="h-4 w-4 shrink-0" />
              <span>{t('footer.contact.phone')}</span>
            </li>
            <li className="flex items-center gap-2">
              <Mail className="h-4 w-4 shrink-0" />
              <span>{t('footer.contact.email')}</span>
            </li>
          </ul>
        </div>
      </div>

      <div className="border-t border-border py-4">
        <p className="container-page text-center text-xs text-muted-foreground">{t('footer.copyright')}</p>
      </div>
    </footer>
  );
}
