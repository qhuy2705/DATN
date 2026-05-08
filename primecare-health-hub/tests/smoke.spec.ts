import { expect, test } from '@playwright/test';

test('login page renders', async ({ page }) => {
  await page.goto('/login');

  await expect(page.getByRole('heading', { name: /Đăng nhập|Login/i })).toBeVisible();
});

test('internal app route guard redirects unauthenticated users to login', async ({ page }) => {
  await page.goto('/app/appointments');

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: /Đăng nhập|Login/i })).toBeVisible();
});

test('public booking page renders', async ({ page }) => {
  await page.goto('/booking');

  await expect(page.getByRole('heading', { name: /Đặt lịch khám bệnh|Book an appointment/i })).toBeVisible();
});
