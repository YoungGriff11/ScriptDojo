import { Page } from '@playwright/test'

export const TEST_USER = {
  username: 'Conor',
  password: 'Conor123!',
}

export const TEST_FILE_NAME = 'TestFile'

/**
 * Logs in with the test user credentials.
 * Call this at the start of any test that requires authentication.
 */
export async function login(page: Page) {
  await page.goto('/login')
  await page.fill('input[name="username"]', TEST_USER.username)
  await page.fill('input[name="password"]', TEST_USER.password)
  await page.click('button[type="submit"]')
  await page.waitForURL('**/dashboard')
}

/**
 * Registers a new test user.
 * Only needed for signup tests.
 */
export async function register(page: Page, username: string, password: string, email: string) {
  await page.goto('/signup')
  await page.fill('input[name="username"]', username)
  await page.fill('input[name="password"]', password)
  await page.fill('input[name="email"]', email)
  await page.click('button[type="submit"]')
}