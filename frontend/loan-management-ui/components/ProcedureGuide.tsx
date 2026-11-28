"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowUpRight, BookOpen, Search } from "lucide-react";

export interface Procedure {
  title: string;
  area: string;
  path: string;
  keywords?: string;
  steps: string[];
  check?: string;
}

const commonWords = new Set(["a", "an", "and", "can", "do", "for", "how", "i", "in", "is", "my", "the", "to"]);

export default function ProcedureGuide({
  title, guides, placeholder, footer, sourcePage = "", guidePath,
}: {
  title: string;
  guides: Procedure[];
  placeholder: string;
  footer?: string;
  sourcePage?: string;
  guidePath: string;
}) {
  const areas = Array.from(new Set(guides.map((guide) => guide.area)));
  const [query, setQuery] = useState("");
  const [area, setArea] = useState("");
  const terms = (query.toLowerCase().match(/[a-z0-9]+/g) ?? []).filter((term) => !commonWords.has(term));
  const contextualPath = guides
    .map((guide) => guide.path.split("#")[0])
    .filter((path) => sourcePage === path || (path !== "/" && path !== "/dashboard" && sourcePage.startsWith(path + "/")))
    .sort((first, second) => second.length - first.length)[0];
  const results = guides
    .filter((guide) => !area || guide.area === area)
    .map((guide) => {
      const title = guide.title.toLowerCase();
      const topic = [guide.area, guide.keywords, ...guide.steps, guide.check].join(" ").toLowerCase();
      const score = terms.reduce((total, term) => total + (title.includes(term) ? 3 : topic.includes(term) ? 1 : 0), 0);
      return { guide, score };
    })
    .filter(({ guide, score }) => terms.length ? score > 0 : !contextualPath || area || guide.path.split("#")[0] === contextualPath)
    .sort((first, second) => second.score - first.score);

  return (
    <section className="mx-auto max-w-5xl space-y-6">
      <header className="rounded-2xl border-t-4 border-[#F4C430] bg-[#0B1F3A] p-6 text-white sm:p-8">
        <div className="flex items-center gap-3">
          <BookOpen className="text-[#F4C430]" size={24} aria-hidden="true" />
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h1>
        </div>
        <label htmlFor="guide-query" className="mt-5 block text-sm font-medium">What would you like to do?</label>
        <div className="relative mt-2">
          <Search className="pointer-events-none absolute left-3 top-3.5 text-slate-500" size={18} aria-hidden="true" />
          <input
            id="guide-query"
            type="search"
            placeholder={placeholder}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="w-full rounded-xl bg-white py-3 pl-10 pr-4 text-sm text-slate-950 outline-offset-4 focus-visible:outline focus-visible:outline-[#F4C430]"
          />
        </div>
      </header>

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-950">
            {contextualPath && !query && !area ? "Guides for this page" : "Procedures"}
          </h2>
          <p aria-live="polite" className="mt-1 text-sm text-slate-600">
            {results.length} {results.length === 1 ? "guide" : "guides"} found
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {contextualPath && (
            <Link href={guidePath} onClick={() => { setQuery(""); setArea(""); }} className="text-sm font-semibold text-[#0B1F3A] underline underline-offset-4">
              Browse all guides
            </Link>
          )}
          <label className="flex items-center gap-2 text-sm text-slate-700">
            Area
            <select value={area} onChange={(event) => setArea(event.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
              <option value="">All areas</option>
              {areas.map((name) => <option key={name}>{name}</option>)}
            </select>
          </label>
        </div>
      </div>

      <div className="space-y-3">
        {results.length === 0 && (
          <p className="rounded-xl border border-slate-200 bg-white p-5 text-sm text-slate-700">
            Try a shorter task name or choose All areas to search the entire guide.
          </p>
        )}
        {results.map(({ guide }) => (
          <details key={guide.title} open={results.length === 1}
            className="rounded-xl border border-slate-200 bg-white">
            <summary className="cursor-pointer rounded-xl px-5 py-4 font-semibold text-[#0B1F3A] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#0B1F3A]">
              {guide.title}
              <span className="mt-1 block pl-4 text-xs font-normal text-slate-600 sm:ml-3 sm:mt-0 sm:inline sm:pl-0">{guide.area}</span>
            </summary>
            <div className="space-y-4 border-t border-slate-100 px-5 py-4 text-sm text-slate-700">
              <ol className="list-decimal space-y-2 pl-5 leading-6">
                {guide.steps.map((step) => <li key={step}>{step}</li>)}
              </ol>
              {guide.check && <p className="rounded-lg border border-[#E6D6A0] bg-[#FFFBEC] p-3 leading-6 text-slate-800">{guide.check}</p>}
              <Link href={guide.path} className="inline-flex items-center gap-1.5 font-semibold text-[#0B1F3A] underline underline-offset-4">
                Open task <ArrowUpRight size={16} aria-hidden="true" />
              </Link>
            </div>
          </details>
        ))}
      </div>
      {footer && <p className="text-sm text-slate-600">{footer}</p>}
    </section>
  );
}
