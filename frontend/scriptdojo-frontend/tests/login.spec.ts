import { test, expect } from '@playwright/test'
import { TEST_USER } from './helpers/auth'

test.describe('Login Page', () => {

  test('should display login form', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByPlaceholder('Username')).toBeVisible()
    await expect(page.getByPlaceholder('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Log In' })).toBeVisible()
  })

  test('should display ScriptDojo heading', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should show error on invalid credentials', async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="username"]', 'wronguser')
    await page.fill('input[name="password"]', 'wrongpassword')
    await page.click('button[type="submit"]')
    await expect(page.getByText('Invalid username or password')).toBeVisible()
  })

  test('should navigate to dashboard on valid login', async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="username"]', TEST_USER.username)
    await page.fill('input[name="password"]', TEST_USER.password)
    await page.click('button[type="submit"]')
    await expect(page).toHaveURL(/\/dashboard/)
  })

  test('should have link to signup page', async ({ page }) => {
    await page.goto('/login')
    await page.getByText("Don't have an account?").click()
    await expect(page).toHaveURL(/\/signup/)
  })

  test('should require username field', async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="password"]', 'somepassword')
    await page.click('button[type="submit"]')
    // HTML5 validation should prevent submission
    const input = page.locator('input[name="username"]')
    await expect(input).toBeFocused()
  })

  test('should require password field', async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="username"]', 'someuser')
    await page.click('button[type="submit"]')
    const input = page.locator('input[name="password"]')
    await expect(input).toBeFocused()
  })

})