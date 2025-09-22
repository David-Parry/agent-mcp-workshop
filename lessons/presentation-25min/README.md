# Deep Dive MCP Server Protocol JSON-RPC Communication Analysis

This directory contains an interactive HTML presentation that explores the Model Context Protocol (MCP) communication flow.

## Presentation Content

The main presentation is located in `index.html`. Below is the rendered content:

---

<div style="background: #0b1020; color: #e6e9ef; font-family: Inter, system-ui, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.5; margin: 0; padding: 20px;">

<header style="padding: 28px 24px; border-bottom: 1px solid #1f2740; background: linear-gradient(180deg, #0d1530, transparent);">
<h1 style="font-size: 36px; margin: 0 0 6px; color: #e6e9ef;">Deep Dive MCP Server Protocol JSON‑RPC Communication Analysis</h1>
<p style="margin: 0; color: #9aa4b2;">Interactive exploration of Model Context Protocol communication flow, runtime facts, and trace logs.</p>
</header>

<main style="max-width: 1100px; margin: 0 auto; padding: 24px;">

<div style="text-align: center; margin-bottom: 32px;">
<h2 style="color: #7aa2f7; margin-bottom: 16px;">Explore the MCP Communication Session</h2>
<p>This workshop breaks down a complete MCP (Model Context Protocol) communication session into three focused views. Each page provides a different perspective on the same underlying JSON-RPC communication between client and server.</p>
</div>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 18px; margin-top: 24px;">

<div style="background: #141a2e; border: 1px solid #1f2740; border-radius: 12px; padding: 20px;">
<span style="font-size: 32px; margin-bottom: 12px; display: block;">📊</span>
<h3 style="margin: 0 0 12px; color: #7aa2f7; font-size: 20px;">Flow Diagram</h3>
<p style="margin: 0; color: #9aa4b2; line-height: 1.6;">Interactive sequence diagram showing the complete MCP communication lifecycle from initialization through tool discovery and execution to graceful shutdown. Visualizes the bidirectional JSON-RPC message flow between client and server.</p>
<p><a href="flow-diagram.html" style="color: #7aa2f7;">→ View Flow Diagram</a></p>
</div>

<div style="background: #141a2e; border: 1px solid #1f2740; border-radius: 12px; padding: 20px;">
<span style="font-size: 32px; margin-bottom: 12px; display: block;">📋</span>
<h3 style="margin: 0 0 12px; color: #7aa2f7; font-size: 20px;">Runtime Facts</h3>
<p style="margin: 0; color: #9aa4b2; line-height: 1.6;">Key information extracted from the communication session including protocol versions, client/server details, capabilities negotiated, available tools, and session flow summary. Perfect for understanding the technical specifications.</p>
<p><a href="runtime-facts.html" style="color: #7aa2f7;">→ View Runtime Facts</a></p>
</div>

<div style="background: #141a2e; border: 1px solid #1f2740; border-radius: 12px; padding: 20px;">
<span style="font-size: 32px; margin-bottom: 12px; display: block;">🔍</span>
<h3 style="margin: 0 0 12px; color: #7aa2f7; font-size: 20px;">Trace Log</h3>
<p style="margin: 0; color: #9aa4b2; line-height: 1.6;">Raw communication trace showing all JSON-RPC messages exchanged with timestamps and message types. Includes interactive controls for filtering, formatting, and analyzing the low-level protocol communication.</p>
<p><a href="trace-log.html" style="color: #7aa2f7;">→ View Trace Log</a></p>
</div>

</div>

<section style="background: #141a2e; border: 1px solid #1f2740; border-radius: 18px; box-shadow: 0 10px 30px rgba(0,0,0,.25); padding: 18px; margin-top: 32px;">
<h2 style="margin: 0 0 16px; color: #7aa2f7;">About This Session</h2>
<p>This analysis is based on a real MCP communication session between:</p>
<ul style="margin: 16px 0;">
<li><strong>Client:</strong> mcp-workshop v1.0.0</li>
<li><strong>Server:</strong> agent-mcp-workshop v0.0.1</li>
<li><strong>Protocol:</strong> MCP 2025-06-18</li>
<li><strong>Tools Available:</strong> key_word_search</li>
</ul>
<p>The session demonstrates the complete MCP lifecycle including initialization, capability negotiation, tool discovery, and graceful shutdown. Each page provides a different lens for understanding this communication pattern.</p>
</section>

<div style="margin-top: 18px; color: #9aa4b2; font-size: 12px; text-align: center;">© 2025 MCP Workshop. Multi-page analysis of JSON-RPC communication flow.</div>

</main>

</div>

---

## Files

- **[index.html](./index.html)** - Main presentation page
- **[flow-diagram.html](./flow-diagram.html)** - Interactive sequence diagram  
- **[runtime-facts.html](./runtime-facts.html)** - Session runtime information
- **[trace-log.html](./trace-log.html)** - Raw communication trace

## Usage

To view the full interactive presentation, open the HTML file:

```bash
open index.html
```