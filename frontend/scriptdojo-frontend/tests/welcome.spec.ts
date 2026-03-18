import { test, expect } from '@playwright/test'

test.describe('Welcome Page', () => {

  test('should display ScriptDojo title', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should display subtitle and tagline', async ({ page }) => {
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

  test('Login button navigates to /login', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page).toHaveURL(/\/login/)
  })

  test('Sign Up button navigates to /signup', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Sign Up Free' }).click()
    await expect(page).toHaveURL(/\/signup/)
  })

})