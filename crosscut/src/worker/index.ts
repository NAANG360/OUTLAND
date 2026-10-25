export default {
  async fetch(request: Request, env: { ASSETS: Fetcher }): Promise<Response> {
    const response = await env.ASSETS.fetch(request)
    const headers = new Headers(response.headers)
    headers.set('X-Content-Type-Options', 'nosniff')
    headers.set('Referrer-Policy', 'strict-origin-when-cross-origin')
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers })
  },
} satisfies ExportedHandler<{ ASSETS: Fetcher }>
