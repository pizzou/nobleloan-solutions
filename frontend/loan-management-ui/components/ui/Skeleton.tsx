export function PageSpinner() {
  return (
    <div className="flex min-h-[360px] items-center justify-center">
      <div className="text-center">
        <div className="mx-auto h-10 w-10 animate-spin rounded-full border-[3px] border-slate-200 border-t-[#0B1F3A] border-r-[#C8A84E]" />
        <p className="mt-4 text-sm font-bold text-slate-800">
          Preparing your financial workspace
        </p>
        <p className="mt-1 text-xs text-slate-400">
          Securely loading current portfolio data…
        </p>
      </div>
    </div>
  );
}
