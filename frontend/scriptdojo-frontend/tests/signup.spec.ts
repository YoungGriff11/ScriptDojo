import { test, expect } from '@playwright/test'

test.describe('Signup Page', () => {

  test('should display signup form', async ({ page }) => {
    await page.goto('/signup')
    await expect(page.getByPlaceholder(/Username/)).toBeVisible()
    await expect(page.getByPlaceholder(/Password/)).toBeVisible()
    await expect(page.getByPlaceholder(/Email/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign Up' })).toBeVisible()
  })

  test('should show success message on valid registration', async ({ page }) => {
    const unique = Date.now()
    await page.goto('/signup')
    await page.fill('input[name="username"]', `user${unique}`)
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', `user${unique}@test.com`)
    await page.click('button[type="submit"]')
    await expect(page.getByText(/Account created successfully/i)).toBeVisible({ timeout: 5000 })
  })

  test('should redirect to login after successful signup', async ({ page }) => {
    const unique = Date.now()
    await page.goto('/signup')
    await page.fill('input[name="username"]', `user${unique}`)
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', `user${unique}@test.com`)
    await page.click('button[type="submit"]')
    await expect(page).toHaveURL(/\/login/, { timeout: 5000 })
  })

  test('should show error on duplicate username', async ({ page }) => {
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'testuser')
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', 'duplicate@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/already taken|already exists/i)).toBeVisible({ timeout: 5000 })
  })

  test('should show inline error for username too short', async ({ page }) => {
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'ab')
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', 'test@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/at least 3 characters/i)).toBeVisible()
  })

  test('should show inline error for password too short', async ({ page }) => {
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'validuser')
    await page.fill('input[name="password"]', '123')
    await page.fill('input[name="email"]', 'test@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/at least 6 characters/i)).toBeVisible()
  })

  test('should show inline error for invalid username characters', async ({ page }) => {
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'invalid user!')
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', 'test@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/letters, numbers and underscores/i)).toBeVisible()
  })

  test('should have link back to login', async ({ page }) => {
    await page.goto('/signup')
    await page.getByText('Already have an account?').click()
    await expect(page).toHaveURL(/\/login/)
  })

})