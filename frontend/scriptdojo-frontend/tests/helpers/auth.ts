import { Page } from '@playwright/test'

/**
 * Shared test credentials and helper functions for Playwright end-to-end tests.
 * TEST_USER must exist in the database before any test that calls login() runs.
 * This account is created once manually (or via a setup script) and reused
 * across all authenticated test suites — it is never created or deleted by
 * individual tests.
 * TEST_FILE_NAME is a shared prefix for file names created during tests.
 * Individual tests append Date.now() to ensure uniqueness across runs.
 */

/** Credentials for the pre-existing test account used across all authenticated tests. */
export const TEST_USER = {
  username: 'Conor',
  password: 'Conor123!',
}

/** Base filename prefix used by dashboard and editor tests when creating test files. */
export const TEST_FILE_NAME = 'TestFile'

/**
 * Logs in with the shared test user credentials and waits for the dashboard to load.
 * Navigates to /login, fills the form, submits, and waits for the URL to reach
 * /dashboard — confirming the session was established before the test proceeds.
 * Call this at the start of any test or beforeEach block that requires authentication.
 * @param {Page} page - the Playwright page instance for the current test
 */
export async function login(page: Page) {
  await page.goto('/login')
  await page.fill('input[name="username"]', TEST_USER.username)
  await page.fill('input[name="password"]', TEST_USER.password)
  await page.click('button[type="submit"]')
  // Wait for the dashboard URL to confirm the login succeeded before returning
  await page.waitForURL('**/dashboard')
}

/**
 * Registers a new user via the signup page.
 * Navigates to /signup, fills the registration form, and submits.
 * Does not wait for a success state — callers should assert the outcome themselves.
 * Only needed for signup-specific tests. General authenticated tests should use
 * login() with the shared TEST_USER account instead.
 * @param {Page}   page     - the Playwright page instance for the current test
 * @param {string} username - the username to register
 * @param {string} password - the password to register
 * @param {string} email    - the email address to register
 */
export async function register(page: Page, username: string, password: string, email: string) {
  await page.goto('/signup')
  await page.fill('input[name="username"]', username)
  await page.fill('input[name="password"]', password)
  await page.fill('input[name="email"]', email)
  await page.click('button[type="submit"]')
}