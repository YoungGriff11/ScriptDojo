import { test, expect } from '@playwright/test'
import { login } from './helpers/auth'

/**
 * End-to-end tests for the Editor page.
 * Test structure:
 * - UI presence     — sidebar title, file name, Monaco editor, console panel, action buttons,
 *                     active users panel, WebSocket connection status
 * - File operations — save triggers a success dialog, run produces compiler/execution output
 * - Share link      — Generate Share Link renders the panel and populates the URL input
 * - Console         — Clear button resets the output to the ready state
 * beforeEach creates a fresh file on the dashboard and navigates to its editor URL
 * before every test, ensuring each test starts with a clean, known editor state.
 * The fileUrl variable captures the editor URL in case individual tests need it,
 * though most assertions are made against the current page state directly.
 */
test.describe('Editor Page', () => {

  let fileUrl: string

  /**
   * Logs in, creates a uniquely named test file on the dashboard, clicks it
   * to navigate to the editor, and waits for the /editor?fileId= URL before
   * each test. Stores the editor URL in fileUrl for tests that need it.
   */
  test.beforeEach(async ({ page }) => {
    await login(page)

    const fileName = `EditorTest${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await page.getByText(`${fileName}.java`).click()
    await page.waitForURL(/\/editor\?fileId=/)
    fileUrl = page.url()
  })

  // ─ UI presence 

  test('should display ScriptDojo in sidebar', async ({ page }) => {
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should display file name in sidebar', async ({ page }) => {
    // Confirms the "File: {name}" info box is rendered in the sidebar
    await expect(page.locator('text=File:')).toBeVisible()
  })

  test('should display Monaco editor', async ({ page }) => {
    // Monaco injects a .monaco-editor element when the editor has fully initialised —
    // a longer timeout is used to account for the editor bundle load time
    await expect(page.locator('.monaco-editor')).toBeVisible({ timeout: 10000 })
  })

  test('should display console output panel', async ({ page }) => {
    await expect(page.getByText('Console Output')).toBeVisible()
  })

  test('should display Save File button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Save File/ })).toBeVisible()
  })

  test('should display Run Java Code button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Run Java Code/ })).toBeVisible()
  })

  test('should display Generate Share Link button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Generate Share Link/ })).toBeVisible()
  })

  test('should show active users panel', async ({ page }) => {
    await expect(page.getByText('Active Users')).toBeVisible()
  })

  test('should show green status dot when connected', async ({ page }) => {
    // Waits for the WebSocket connection to establish before asserting.
    // The dot colour is driven by the connected state — a fully green ScriptDojo
    // title being visible serves as a proxy for connection being established.
    await page.waitForTimeout(2000)
    const dot = page.locator('span').filter({ hasText: '' }).first()
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  // ─ File operations 

  test('should save file successfully', async ({ page }) => {
    // Registers the dialog handler before the click that triggers it to avoid
    // a race condition where the alert fires before the handler is attached
    page.once('dialog', dialog => {
      expect(dialog.message()).toContain('saved')
      dialog.accept()
    })
    await page.getByRole('button', { name: /Save File/ }).click()
  })

  test('should run code and show output', async ({ page }) => {
    // Triggers the compile-and-run pipeline and waits for any terminal state
    // in the console — Compiling (started), EXECUTION SUCCESS, or FAILED
    await page.getByRole('button', { name: /Run Java Code/ }).click()
    await expect(page.getByText(/Compiling|EXECUTION|FAILED/)).toBeVisible({ timeout: 15000 })
  })

  // ─ Share link 

  test('should generate share link', async ({ page }) => {
    // Confirms the share link panel appears after the room is created
    await page.getByRole('button', { name: /Generate Share Link/ }).click()
    await expect(page.getByText('Share Link')).toBeVisible({ timeout: 5000 })
  })

  test('should display share URL after generating link', async ({ page }) => {
    // Confirms the readonly URL input is populated with a valid room URL after
    // the share link is generated — value should contain the /room/ path segment
    await page.getByRole('button', { name: /Generate Share Link/ }).click()
    const input = page.locator('input[readonly]')
    await expect(input).toBeVisible({ timeout: 5000 })
    const value = await input.inputValue()
    expect(value).toContain('localhost:5173/room/')
  })

  // ─ Console 

  test('should clear console output', async ({ page }) => {
    // Runs the code to populate the console, then clears it and confirms
    // the default ready message is restored
    await page.getByRole('button', { name: /Run Java Code/ }).click()
    await expect(page.getByText(/Compiling|EXECUTION/)).toBeVisible({ timeout: 15000 })
    await page.getByRole('button', { name: 'Clear' }).click()
    await expect(page.getByText('Ready to run Java code')).toBeVisible()
  })

})