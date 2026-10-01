import Link from "next/link";
import { SITE_CONTENT } from "../../../lib/siteContent";

export default function TestimonialsPage() {
  const { testimonials, name, primaryColor, accentColor } = SITE_CONTENT;
  return (
    <main className="bg-white text-slate-950">
      <section className="bg-[#061326] text-white">
        <div className="mx-auto max-w-7xl px-5 py-20 sm:px-8">
          <div className="max-w-3xl">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accentColor }}
            >
              Client stories
            </div>
            <h1 className="mt-4 text-5xl font-black tracking-[-.05em] sm:text-6xl">
              What clients say about {name}.
            </h1>
            <p className="mt-6 text-base leading-7 text-white/60 sm:text-lg">
              Published customer stories and experiences from our lending
              services.
            </p>
          </div>
        </div>
      </section>
      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-24">
        <div className="grid gap-6 md:grid-cols-3">
          {testimonials.map((item) => (
            <article
              key={`${item.name}-${item.role}`}
              className="rounded-[28px] border border-slate-200 bg-white p-7 shadow-sm"
            >
              <div
                className="text-lg tracking-[.15em]"
                style={{ color: accentColor }}
              >
                {"★".repeat(Math.max(0, Math.min(5, item.rating)))}
              </div>
              <blockquote className="mt-5 text-base font-semibold leading-7 text-slate-700">
                “{item.text}”
              </blockquote>
              <div className="mt-8 border-t border-slate-100 pt-5">
                <div className="font-black">{item.name}</div>
                <div className="mt-1 text-xs text-slate-500">{item.role}</div>
              </div>
            </article>
          ))}
        </div>
        <div className="mt-12 rounded-[28px] bg-slate-50 p-8 text-center">
          <h2 className="text-2xl font-black">Ready to start?</h2>
          <p className="mx-auto mt-3 max-w-xl text-sm leading-7 text-slate-500">
            Review our lending solutions, submit an application, or track an
            existing application.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link
              href="/services"
              className="rounded-xl px-5 py-3 text-sm font-black text-white"
              style={{ backgroundColor: primaryColor }}
            >
              View solutions
            </Link>
            <Link
              href="/apply"
              className="rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-black text-slate-800"
            >
              Apply now
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
