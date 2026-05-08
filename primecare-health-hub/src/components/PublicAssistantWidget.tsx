import { useEffect, useRef, useState } from 'react';
import { Bot, Loader2, SendHorizonal, Sparkles, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { useAskPublicAssistant } from '@/hooks/use-public-data';
import { getApiErrorMessage } from '@/lib/error-utils';
import { cn } from '@/lib/utils';
import type {
  PublicAssistantAction,
  PublicAssistantMessagePayload,
  PublicAssistantRequestPayload,
  PublicAssistantResponse,
} from '@/types/api';

interface ChatMessage {
  id: string;
  role: 'assistant' | 'user';
  text: string;
  provider?: string;
  caution?: string;
  actions?: PublicAssistantAction[];
  suggestions?: string[];
}

const STORAGE_KEY = 'primecare_public_ai_chat_v2';
const createId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

const createWelcomeMessage = (isEn: boolean): ChatMessage => ({
  id: createId(),
  role: 'assistant',
  text: isEn
    ? 'Hello. I am PrimeCare AI. I can guide you through booking, choosing the right specialty, preparing before a visit, and looking up your appointment or result.'
    : 'Xin chào. Tôi là PrimeCare AI. Tôi có thể hỗ trợ bạn đặt lịch, chọn đúng chuyên khoa, chuẩn bị trước khi đi khám và tra cứu phiếu hẹn hoặc kết quả.',
  provider: 'PRIMECARE AI',
  caution: isEn
    ? 'I support public guidance only. For diagnosis or treatment decisions, please follow the clinician’s official conclusion.'
    : 'Tôi chỉ hỗ trợ hướng dẫn công khai. Với chẩn đoán hoặc quyết định điều trị, vui lòng làm theo kết luận chính thức của nhân viên y tế.',
  suggestions: isEn
    ? ['How do I book an appointment?', 'How can I look up my result PDF?', 'Do blood tests require fasting?']
    : ['Làm sao để đặt lịch khám?', 'Tra cứu PDF kết quả như thế nào?', 'Xét nghiệm máu có cần nhịn ăn không?'],
});

const normalizeStoredMessages = (value: unknown): ChatMessage[] | null => {
  if (!Array.isArray(value)) return null;
  const messages = value
    .filter((item): item is Partial<ChatMessage> => Boolean(item && typeof item === 'object'))
    .map((item) => ({
      id: typeof item.id === 'string' ? item.id : createId(),
      role: item.role === 'user' ? 'user' : 'assistant',
      text: typeof item.text === 'string' ? item.text : '',
      provider: typeof item.provider === 'string' ? item.provider : undefined,
      caution: typeof item.caution === 'string' ? item.caution : undefined,
      actions: Array.isArray(item.actions) ? item.actions : undefined,
      suggestions: Array.isArray(item.suggestions)
        ? item.suggestions.filter((entry): entry is string => typeof entry === 'string')
        : undefined,
    }))
    .filter((item) => item.text.trim().length > 0);
  return messages.length ? messages : null;
};

const getCurrentPageTitle = (pathname: string, isEn: boolean) => {
  switch (pathname) {
    case '/booking':
      return isEn ? 'Booking page' : 'Trang đặt lịch';
    case '/appointments/lookup':
      return isEn ? 'Lookup page' : 'Trang tra cứu';
    case '/specialties':
      return isEn ? 'Specialties page' : 'Trang chuyên khoa';
    case '/doctors':
      return isEn ? 'Doctors page' : 'Trang bác sĩ';
    case '/branches':
      return isEn ? 'Branches page' : 'Trang cơ sở';
    case '/medical-services':
      return isEn ? 'Medical services page' : 'Trang dịch vụ';
    case '/faq':
      return isEn ? 'FAQ page' : 'Trang câu hỏi thường gặp';
    case '/contact':
      return isEn ? 'Contact page' : 'Trang liên hệ';
    default:
      return isEn ? 'PrimeCare website' : 'Website PrimeCare';
  }
};

const normalizeProviderLabel = (provider?: string, isEn?: boolean) => {
  if (!provider) return 'AI';
  if (provider === 'GEMINI_2_5_FLASH') {
    return 'Gemini 2.5 Flash';
  }
  if (provider === 'LIVE_AI') {
    return isEn ? 'Live AI' : 'AI trực tiếp';
  }
  if (provider === 'GUIDED_ASSISTANT') {
    return isEn ? 'Guided AI' : 'AI định hướng';
  }
  return provider;
};

export function PublicAssistantWidget() {
  const { i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const isEn = i18n.language?.startsWith('en');
  const askAssistant = useAskPublicAssistant();
  const endRef = useRef<HTMLDivElement | null>(null);

  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    if (typeof window === 'undefined') return [createWelcomeMessage(false)];
    try {
      const raw = window.sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return [createWelcomeMessage(false)];
      const parsed = JSON.parse(raw);
      return normalizeStoredMessages(parsed) ?? [createWelcomeMessage(false)];
    } catch {
      return [createWelcomeMessage(false)];
    }
  });


  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, open]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-20)));
  }, [messages]);

  useEffect(() => {
    setMessages((current) =>
      current.length === 1 &&
      current[0]?.role === 'assistant' &&
      current[0]?.provider === 'PRIMECARE AI'
        ? [createWelcomeMessage(isEn)]
        : current,
    );
  }, [isEn]);

  const normalizeActionPath = (value?: string) => {
    if (!value) return '/';
    return value === '/lookup' ? '/appointments/lookup' : value;
  };

  const focusLookupField = (target: 'appointmentLookupCode' | 'resultLookupCode') => {
    const targetPath = target === 'resultLookupCode' ? '/results/lookup' : '/appointments/lookup';
    if (location.pathname !== targetPath) {
      navigate(`${targetPath}?focus=${encodeURIComponent(target)}`);
      setOpen(false);
      return;
    }
    const element = document.getElementById(target) as HTMLInputElement | null;
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    window.setTimeout(() => element?.focus(), 180);
    setOpen(false);
  };

  const handleAssistantAction = (action: PublicAssistantAction) => {
    if (action.type === 'BOOK_APPOINTMENT') {
      navigate(normalizeActionPath(action.value || '/booking'));
      setOpen(false);
      return;
    }
    if (action.type === 'LOOKUP_RESULT') {
      focusLookupField('resultLookupCode');
      return;
    }
    if (action.type === 'LOOKUP_APPOINTMENT') {
      focusLookupField('appointmentLookupCode');
      return;
    }
    navigate(normalizeActionPath(action.value));
    setOpen(false);
  };

  const appendAssistantResponse = (response: PublicAssistantResponse) => {
    setMessages((prev) => [
      ...prev,
      {
        id: createId(),
        role: 'assistant',
        text: response.answer,
        provider: response.provider || 'GUIDED_ASSISTANT',
        caution: response.caution,
        actions: response.actions,
        suggestions: response.suggestions,
      },
    ]);
  };

  const buildPayload = (nextQuestion: string): PublicAssistantRequestPayload => {
    const history: PublicAssistantMessagePayload[] = messages
      .filter((message) => message.text.trim())
      .slice(-8)
      .map((message) => ({ role: message.role, text: message.text }));

    return {
      question: nextQuestion,
      locale: isEn ? 'en' : 'vi',
      pagePath: location.pathname,
      pageTitle: getCurrentPageTitle(location.pathname, isEn),
      history,
    };
  };

  const handleSubmit = async (preset?: string) => {
    const nextQuestion = (preset ?? question).trim();
    if (!nextQuestion) {
      toast.error(isEn ? 'Please enter a question.' : 'Vui lòng nhập câu hỏi.');
      return;
    }

    setOpen(true);
    setMessages((prev) => [...prev, { id: createId(), role: 'user', text: nextQuestion }]);
    setQuestion('');

    try {
      const response = await askAssistant.mutateAsync(buildPayload(nextQuestion));
      appendAssistantResponse(response);
    } catch (error) {
      toast.error(
        getApiErrorMessage(
          error,
          isEn ? 'Unable to ask the assistant right now.' : 'Không thể gọi trợ lý lúc này.',
        ),
      );
      setMessages((prev) => [
        ...prev,
        {
          id: createId(),
          role: 'assistant',
          text: isEn
            ? 'I cannot answer right now. Please try again in a moment.'
            : 'Tôi chưa thể phản hồi lúc này. Vui lòng thử lại sau ít phút.',
          provider: 'SYSTEM',
        },
      ]);
    }
  };

  const clearConversation = () => {
    setMessages([createWelcomeMessage(isEn)]);
    if (typeof window !== 'undefined') {
      window.sessionStorage.removeItem(STORAGE_KEY);
    }
  };

  return (
    <>
      <div className="fixed bottom-5 right-5 z-40 flex flex-col items-end gap-2">
        {!open ? (
          <div className="hidden rounded-full border border-border/60 bg-background/95 px-3 py-1.5 text-xs text-muted-foreground shadow-sm backdrop-blur md:block">
            {isEn ? 'Need help?' : 'Cần hỗ trợ?'}
          </div>
        ) : null}
        <Button
          type="button"
          size="lg"
          onClick={() => setOpen(true)}
          className="h-14 rounded-full px-5 shadow-lg"
        >
          <Sparkles className="mr-2 h-4 w-4" />
          PrimeCare AI
        </Button>
      </div>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="flex h-full w-full flex-col gap-0 p-0 sm:max-w-[440px]">
          <SheetHeader className="border-b px-5 py-4">
            <div className="flex items-start justify-between gap-3 pr-10">
              <div className="space-y-1 text-left">
                <div className="flex items-center gap-2">
                  <div className="rounded-2xl border border-primary/15 bg-primary/10 p-2 text-primary">
                    <Bot className="h-4 w-4" />
                  </div>
                  <div>
                    <SheetTitle>{isEn ? 'PrimeCare AI assistant' : 'Trợ lý AI PrimeCare'}</SheetTitle>
                    <div className="mt-1 flex items-center gap-2">
                      <Badge variant="secondary">{isEn ? 'Public assistant' : 'Trợ lý công khai'}</Badge>
                      <Badge variant="outline">{getCurrentPageTitle(location.pathname, isEn)}</Badge>
                    </div>
                  </div>
                </div>
                <SheetDescription>
                  {isEn
                    ? 'A context-aware chat helper for booking, specialty guidance, lookup, and visit preparation.'
                    : 'Trợ lý chat có hiểu ngữ cảnh trang, hỗ trợ đặt lịch, chọn chuyên khoa, tra cứu và chuẩn bị trước khám.'}
                </SheetDescription>
              </div>
              <Button type="button" variant="ghost" size="icon" onClick={clearConversation}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </SheetHeader>
          <ScrollArea className="flex-1 bg-muted/20 px-4 py-4">
            <div className="space-y-4 pb-2">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={cn('flex', message.role === 'user' ? 'justify-end' : 'justify-start')}
                >
                  <div
                    className={cn(
                      'max-w-[90%] rounded-3xl px-4 py-3 text-sm shadow-sm',
                      message.role === 'user'
                        ? 'rounded-br-md bg-primary text-primary-foreground'
                        : 'rounded-bl-md border bg-background text-foreground',
                    )}
                  >
                    <div className="flex items-center gap-2">
                      {message.role === 'assistant' ? (
                        <Badge variant="secondary" className="mb-1">
                          {normalizeProviderLabel(message.provider, isEn)}
                        </Badge>
                      ) : null}
                    </div>
                    <p className="whitespace-pre-wrap leading-6">{message.text}</p>
                    {message.caution ? (
                      <div className="mt-3 rounded-2xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                        {message.caution}
                      </div>
                    ) : null}
                    {message.actions?.length ? (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {message.actions.map((action, index) => (
                          <Button
                            key={`${message.id}-${action.type}-${index}`}
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => handleAssistantAction(action)}
                          >
                            {action.label}
                          </Button>
                        ))}
                      </div>
                    ) : null}
                    {message.suggestions?.length ? (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {message.suggestions.map((suggestion, index) => (
                          <Button
                            key={`${message.id}-suggestion-${index}`}
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="h-auto rounded-full border bg-muted/50 px-3 py-1.5 text-xs"
                            onClick={() => void handleSubmit(suggestion)}
                          >
                            {suggestion}
                          </Button>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </div>
              ))}
              {askAssistant.isPending ? (
                <div className="flex justify-start">
                  <div className="flex max-w-[88%] items-center gap-2 rounded-3xl rounded-bl-md border bg-background px-4 py-3 text-sm text-muted-foreground shadow-sm">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    {isEn ? 'PrimeCare AI is thinking...' : 'PrimeCare AI đang xử lý...'}
                  </div>
                </div>
              ) : null}
              <div ref={endRef} />
            </div>
          </ScrollArea>

          <div className="border-t bg-background px-4 py-4">
            <div className="mb-2 text-xs text-muted-foreground">
              {isEn
                ? 'Tip: ask naturally. The assistant will use the current page context to guide you faster.'
                : 'Mẹo: hãy hỏi tự nhiên. Trợ lý sẽ tận dụng ngữ cảnh trang hiện tại để hướng dẫn bạn nhanh hơn.'}
            </div>
            <div className="flex items-end gap-2">
              <Input
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    void handleSubmit();
                  }
                }}
                placeholder={
                  isEn
                    ? 'Ask anything about booking, specialties, OTP lookup, preparation...'
                    : 'Hỏi về đặt lịch, chọn chuyên khoa, OTP tra cứu, chuẩn bị trước khám...'
                }
                className="h-12 rounded-2xl"
              />
              <Button
                type="button"
                className="h-12 rounded-2xl px-4"
                onClick={() => void handleSubmit()}
                disabled={askAssistant.isPending}
              >
                {askAssistant.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <SendHorizonal className="h-4 w-4" />}
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
