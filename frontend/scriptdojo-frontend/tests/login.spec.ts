import { test, expect } from '@playwright/test'
import { TEST_USER } from './helpers/auth'

/**
 * End-to-end tests for the Login page.
 * Test structure:
 * - UI presence      — form fields, heading, signup link
 * - Authentication   — valid credentials navigate to /dashboard,
 *                      invalid credentials show an error message
 * - HTML5 validation — empty username or password field focuses the
 *                      relevant input rather than submitting the form
 * Each test navigates to /login independently — no shared beforeEach state
 * is needed as none of these tests require a pre-authenticated session.
 */
test.describe('Login Page', () => {

  // ─ UI presence 
  test('should display login form', async ({ page }) => {
    // Confirms all three required form elements are present on page load
    await page.goto('/login')
    await expect(page.getByPlaceholder('Username')).toBeVisible()
    await expect(page.getByPlaceholder('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Log In' })).toBeVisible()
  })

  test('should display ScriptDojo heading', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should have link to signup page', async ({ page }) => {
    // Clicks the "Don't have an account?" link and confirms navigation to /signup
    await page.goto('/login')
    await page.getByText("Don't have an account?").click()
    await expect(page).toHaveURL(/\/signup/)
  })

  // ─ Authentication 
  test('should show error on invalid credentials', async ({ page }) => {
    // Submits credentials that do not match any account and confirms the
    // error message from LoginPage.handleSubmit is rendered
    await page.goto('/login')
    await page.fill('input[name="username"]', 'wronguser')
    await page.fill('input[name="password"]', 'wrongpassword')
    await page.click('button[type="submit"]')
    await expect(page.getByText('Invalid username or password')).toBeVisible()
  })

  test('should navigate to dashboard on valid login', async ({ page }) => {
    // Submits the shared TEST_USER credentials and confirms the session is
    // established and the page navigates to /dashboard
    await page.goto('/login')
    await page.fill('input[name="username"]', TEST_USER.username)
    await page.fill('input[name="password"]', TEST_USER.password)
    await page.click('button[type="submit"]')
    await expect(page).toHaveURL(/\/dashboard/)
  })

  // ─ HTML5 validation 

  test('should require username field', async ({ page }) => {
    // Submits with only the password filled — the browser's HTML5 required
    // attribute validation should prevent submission and focus the username input
    await page.goto('/login')
    await page.fill('input[name="password"]', 'somepassword')
    await page.click('button[type="submit"]')
    const input = page.locator('input[name="username"]')
    await expect(input).toBeFocused()
  })

  test('should require password field', async ({ page }) => {
    // Submits with only the username filled — the browser's HTML5 required
    // attribute validation should prevent submission and focus the password input
    await page.goto('/login')
    await page.fill('input[name="username"]', 'someuser')
    await page.click('button[type="submit"]')
    const input = page.locator('input[name="password"]')
    await expect(input).toBeFocused()
  })

})