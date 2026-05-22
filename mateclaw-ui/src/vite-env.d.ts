/// <reference types="vite/client" />

declare module '*.vue' {
	import type { DefineComponent } from 'vue'
	const component: DefineComponent<{}, {}, any>
	export default component
}

declare global {
	interface Window {
		mateclawDesktop?: {
			isDesktop?: boolean
			isPackaged?: boolean
			electronVersion?: string
			getRuntimeInfo?: () => Promise<{
				isDesktop: boolean
				isPackaged: boolean
				electronVersion: string
				localAppUrl: string | null
				backendUrl: string
				proxyUrl?: string
				apiTargetUrl?: string
				localToolHost?: {
					available: boolean
					mode: string
					capabilities: string[]
					sharedPayloadSchema?: string
					harnessIngest?: boolean
				}
				platform: string
				arch: string
				homeDir: string
			}>
			getServerConfig?: () => Promise<{
				backendUrl: string
				proxyUrl: string
				autoStartBackend: boolean
				backendCommand: string
				backendArgs: string[]
				backendCwd: string
				updatedAt: string | null
			}>
			saveServerConfig?: (config: Record<string, unknown>) => Promise<unknown>
			testServer?: (config: Record<string, unknown>) => Promise<{
				success: boolean
				status?: number
				url: string
				body?: string
				message?: string
			}>
			selectDirectory?: () => Promise<string | null>
			selectFiles?: (options?: Record<string, unknown>) => Promise<string[]>
			readTextFile?: (filePath: string) => Promise<string>
			workspaceTree?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			workspaceGlob?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			workspaceGrep?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			readFileSnippet?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			writeWorkspacePatch?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			gitStatus?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			gitDiff?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			runReadonlyCommand?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			prepareApprovalCommand?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			approveCommandRequest?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			denyCommandRequest?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			executeApprovedCommand?: (options?: Record<string, unknown>) => Promise<Record<string, unknown>>
			ingestHarnessToolResult?: (options?: Record<string, unknown>) => Promise<{
				success: boolean
				status: number
				url: string
				body: unknown
			}>
			writeTextFile?: (payload: { filePath: string; content: string }) => Promise<boolean>
			revealPath?: (targetPath: string) => Promise<boolean>
			openPath?: (targetPath: string) => Promise<boolean>
			closeCurrentWindow?: () => Promise<boolean>
		}
	}
}

export {}
