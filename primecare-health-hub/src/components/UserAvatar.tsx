import { useEffect, useMemo, useState } from 'react';
import { resolveImagePreviewUrl } from '@/lib/api-url';

interface UserAvatarProps {
  name: string;
  avatarUrl?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const sizes = {
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-16 w-16 text-lg',
};

export function UserAvatar({ name, avatarUrl, size = 'md', className = '' }: UserAvatarProps) {
  const [imageFailed, setImageFailed] = useState(false);
  const initials = name
    .split(' ')
    .map((n) => n[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  const normalizedAvatarUrl = useMemo(() => {
    if (!avatarUrl) return undefined;
    const normalized = resolveImagePreviewUrl(avatarUrl, 'image/*');
    return normalized || undefined;
  }, [avatarUrl]);

  useEffect(() => {
    setImageFailed(false);
  }, [normalizedAvatarUrl]);

  if (normalizedAvatarUrl && !imageFailed) {
    return (
      <img
        src={normalizedAvatarUrl}
        alt={name}
        className={`${sizes[size]} rounded-full object-cover ring-2 ring-background ${className}`}
        onError={() => setImageFailed(true)}
      />
    );
  }

  return (
    <div
      className={`${sizes[size]} rounded-full bg-primary flex items-center justify-center font-semibold text-primary-foreground ring-2 ring-background ${className}`}
    >
      {initials}
    </div>
  );
}
