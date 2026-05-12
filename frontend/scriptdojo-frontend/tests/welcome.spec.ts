import { test, expect } from '@playwright/test'

/**
 * End-to-end tests for the Welcome page (/).
 * Test structure:
 * - UI presence  — title, subtitle, tagline, Login button, Sign Up Free button
 * - Navigation   — Login button navigates to /login,
 *                  Sign Up Free button navigates to /signup
 * Each test navigates to / independently — no authentication or shared state
 * is required as the Welcome page is fully public.
 */
test.describe('Welcome Page', () => {

  // ─ UI presence

  test('should display ScriptDojo title', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should display subtitle and tagline', async ({ page }) => {
    // Confirms both marketing lines from WelcomePage are rendered
    await page.goto('/')
    await expect(page.getByText('Real-time Collaborative Java IDE')).toBeVisible()
    await expect(page.getByText('Like Google Docs')).toBeVisible()
  })

  test('should have Login button', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('button', { name: 'Login' })).toBeVisible()
  })

  test('should have Sign Up Free button', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('button', { name: 'Sign Up Free' })).toBeVisible()
  })

  // ─ Navigation

  test('Login button navigates to /login', async ({ page }) => {
    // Confirms the Login button's onClick handler calls navigate('/login')
    await page.goto('/')
    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page).toHaveURL(/\/login/)
  })

  test('Sign Up button navigates to /signup', async ({ page }) => {
    // Confirms the Sign Up Free button's onClick handler calls navigate('/signup')
    await page.goto('/')
    await page.getByRole('button', { name: 'Sign Up Free' }).click()
    await expect(page).toHaveURL(/\/signup/)
  })

})