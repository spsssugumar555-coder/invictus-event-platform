import { useState } from "react";

/**
 * Live preview of AI-generated HTML. sandbox="allow-scripts" WITHOUT allow-same-origin gives the page an opaque
 * origin, so generated code can run but cannot read the app's storage, cookies or tokens.
 */
export default function PreviewFrame({ html, height = 560 }: { html: string; height?: number }) {
  const [mobile, setMobile] = useState(false);
  return (
    <div>
      <div className="mb-2 flex gap-2" role="group" aria-label="Preview size">
        <button type="button" className={`rounded-md px-3 py-1 text-xs font-semibold ${!mobile ? "bg-raised text-brass" : "text-mist hover:text-paper"}`} onClick={() => setMobile(false)}>Desktop</button>
        <button type="button" className={`rounded-md px-3 py-1 text-xs font-semibold ${mobile ? "bg-raised text-brass" : "text-mist hover:text-paper"}`} onClick={() => setMobile(true)}>Mobile</button>
      </div>
      <div className="overflow-auto rounded-lg border border-edge bg-white">
        <iframe title="Website preview" sandbox="allow-scripts" srcDoc={html}
          className="mx-auto block border-0 bg-white" style={{ width: mobile ? 390 : "100%", height }} />
      </div>
    </div>
  );
}
