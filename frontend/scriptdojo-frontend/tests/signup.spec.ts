import { test, expect } from '@playwright/test'

/**
 * End-to-end tests for the Signup page.
 * Test structure:
 * - UI presence         — all three form fields and the Sign Up button are visible
 * - Happy path          — valid registration shows success message and redirects to /login
 * - Duplicate username  — backend 400 error is mapped to a user-friendly message
 * - Client-side validation — inline field errors for short username, short password,
 *                            and invalid username characters
 * - Navigation          — "Already have an account?" link navigates to /login
 * Each test navigates to /signup independently — no shared beforeEach state is needed.
 * Unique usernames and emails are generated using Date.now() to prevent conflicts
 * with existing accounts in the shared database across test runs.
 * The duplicate username test uses "testuser" as a fixed name that is expected to
 * already exist in the database — this account must be present for that test to pass.
 */
test.describe('Signup Page', () => {

  // ─ UI presence 
  test('should display signup form', async ({ page }) => {
    // Confirms all four required form elements are present on page load
    await page.goto('/signup')
    await expect(page.getByPlaceholder(/Username/)).toBeVisible()
    await expect(page.getByPlaceholder(/Password/)).toBeVisible()
    await expect(page.getByPlaceholder(/Email/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign Up' })).toBeVisible()
  })

  // ─ Happy path 
  test('should show success message on valid registration', async ({ page }) => {
    // Registers a new account with a unique username and email, then confirms
    // the success message from SignupPage.handleSubmit is rendered
    const unique = Date.now()
    await page.goto('/signup')
    await page.fill('input[name="username"]', `user${unique}`)
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', `user${unique}@test.com`)
    await page.click('button[type="submit"]')
    await expect(page.getByText(/Account created successfully/i)).toBeVisible({ timeout: 5000 })
  })

  test('should redirect to login after successful signup', async ({ page }) => {
    // Confirms the 2-second setTimeout redirect to /login fires after the
    // success message is shown — timeout covers the redirect delay
    const unique = Date.now()
    await page.goto('/signup')
    await page.fill('input[name="username"]', `user${unique}`)
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', `user${unique}@test.com`)
    await page.click('button[type="submit"]')
    await expect(page).toHaveURL(/\/login/, { timeout: 5000 })
  })

  // ─ Duplicate username
  test('should show error on duplicate username', async ({ page }) => {
    // Attempts to register with a username that already exists in the database.
    // Confirms parseBackendError() maps the 400 response to a friendly message.
    // Requires "testuser" to already be registered in the test database.
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'testuser')
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', 'duplicate@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/already taken|already exists/i)).toBeVisible({ timeout: 5000 })
  })

  // ─ Client-side validation

  test('should show inline error for username too short', async ({ page }) => {
    // 2-character username violates the @Size(min=3) client-side rule —
    // confirms the inline fieldError is rendered without a network request
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'ab')
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', 'test@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/at least 3 characters/i)).toBeVisible()
  })

  test('should show inline error for password too short', async ({ page }) => {
    // 3-character password violates the @Size(min=6) client-side rule —
    // confirms the inline fieldError is rendered without a network request
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'validuser')
    await page.fill('input[name="password"]', '123')
    await page.fill('input[name="email"]', 'test@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/at least 6 characters/i)).toBeVisible()
  })

  test('should show inline error for invalid username characters', async ({ page }) => {
    // Username containing a space and exclamation mark violates the
    await page.goto('/signup')
    await page.fill('input[name="username"]', 'invalid user!')
    await page.fill('input[name="password"]', 'Password123!')
    await page.fill('input[name="email"]', 'test@test.com')
    await page.click('button[type="submit"]')
    await expect(page.getByText(/letters, numbers and underscores/i)).toBeVisible()
  })

  // ─ Navigation 

  test('should have link back to login', async ({ page }) => {
    // Clicks the "Already have an account?" link and confirms navigation to /login
    await page.goto('/signup')
    await page.getByText('Already have an account?').click()
    await expect(page).toHaveURL(/\/login/)
  })

})