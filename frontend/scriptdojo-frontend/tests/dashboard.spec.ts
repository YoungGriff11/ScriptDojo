import { test, expect } from '@playwright/test'
import { login, TEST_FILE_NAME } from './helpers/auth'

/**
 * End-to-end tests for the Dashboard page.
 * Test structure:
 * - UI presence    — heading, username display, file input, Create File button, Logout button
 * - File creation  — button click, Enter key, .java extension auto-append
 * - Navigation     — clicking a file navigates to /editor?fileId=
 * - File deletion  — dialog confirmation, DELETE request completes, file removed from list
 * - Logout         — redirects to /login
 * Each test starts from a freshly logged-in dashboard state via the login()
 * helper in @BeforeEach. File names include Date.now() to ensure uniqueness
 * across test runs and prevent cross-test state conflicts in the shared database.
 */
test.describe('Dashboard Page', () => {

  // Log in before every test so each assertion starts from an authenticated state
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  // ─ UI presence 

  test('should display ScriptDojo heading', async ({ page }) => {
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should display logged in username', async ({ page }) => {
    // Verifies the "Logged in as: {username}" header is rendered after login
    await expect(page.getByText(/Logged in as/)).toBeVisible()
  })

  test('should display file creation input', async ({ page }) => {
    await expect(page.getByPlaceholder(/New file name/)).toBeVisible()
  })

  test('should display Create File button', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Create File' })).toBeVisible()
  })

  // ─ File creation 

  test('should create a new file', async ({ page }) => {
    // Types a filename and clicks Create File, then confirms the file appears in the list
    const fileName = `${TEST_FILE_NAME}${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await expect(page.getByText(fileName)).toBeVisible({ timeout: 5000 })
  })

  test('should create file on Enter key press', async ({ page }) => {
    // Confirms the onKeyDown Enter handler in DashboardPage triggers file creation
    const fileName = `EnterTest${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.keyboard.press('Enter')
    await expect(page.getByText(fileName)).toBeVisible({ timeout: 5000 })
  })

  test('should auto-append .java to file name', async ({ page }) => {
    // Confirms DashboardPage.createFile() appends .java when the user omits the extension
    const baseName = `AutoJava${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', baseName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await expect(page.getByText(`${baseName}.java`)).toBeVisible({ timeout: 5000 })
  })

  // ─ Navigation 

  test('should navigate to editor when file is clicked', async ({ page }) => {
    // Creates a file, clicks its name, and confirms the URL changes to /editor?fileId=
    const fileName = `ClickTest${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await page.getByText(`${fileName}.java`).click()
    await expect(page).toHaveURL(/\/editor\?fileId=/)
  })

  // ─ File deletion 

  test('should delete a file', async ({ page }) => {
    // Creates a file, accepts the window.confirm dialog, waits for the DELETE
    // request to complete, then verifies the file is no longer in the list.
    // The dialog handler is registered BEFORE the click that triggers it —
    // registering it after would cause a race condition where the dialog fires
    // before the handler is attached.
    const fileName = `DeleteMe${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await expect(page.getByText(`${fileName}.java`)).toBeVisible({ timeout: 5000 })

    page.once('dialog', dialog => dialog.accept())
    await page.getByRole('button', { name: 'Delete' }).last().click()

    // Wait for the DELETE response before asserting absence — without this,
    // the assertion may run before the file list has been refreshed
    await page.waitForResponse(res =>
      res.url().includes('/api/files/') && res.request().method() === 'DELETE'
    )

    await expect(page.getByText(`${fileName}.java`)).not.toBeVisible({ timeout: 5000 })
  })

  // ─ Logout 
  test('should show logout button', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()
  })

  test('should logout and redirect to login', async ({ page }) => {
    // Confirms the Logout button clears the session and navigates to /login
    await page.getByRole('button', { name: 'Logout' }).click()
    await expect(page).toHaveURL(/\/login/)
  })

})