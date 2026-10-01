import Link from "next/link";
import { SITE_CONTENT } from "../../../lib/siteContent";

export default function TeamPage() {
  const { team, name, primaryColor, accentColor } = SITE_CONTENT;

  return (
    <main className="bg-white text-slate-950">
      <section className="bg-[#061326] text-white">
        <div className="mx-auto max-w-7xl px-5 py-20 sm:px-8">
          <div className="max-w-3xl">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accentColor }}
            >
              Our people
            </div>

            <h1 className="mt-4 text-5xl font-black tracking-[-.05em] sm:text-6xl">
              Meet the {name} team.
            </h1>

            <p className="mt-6 text-base leading-7 text-white/60 sm:text-lg">
              The people responsible for delivering our lending services and
              supporting our clients.
            </p>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-24">
        {team.length > 0 ? (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {team.map((member) => (
              <article
                key={`${member.name}-${member.role}`}
                className="rounded-[28px] border border-slate-200 bg-white p-7 shadow-sm"
              >
                <div
                  className="flex h-20 w-20 items-center justify-center rounded-2xl text-xl font-black"
                  style={{
                    backgroundColor: `${primaryColor}10`,
                    color: primaryColor,
                  }}
                >
                  {member.initials}
                </div>

                <h2 className="mt-6 text-xl font-black">{member.name}</h2>

                <p
                  className="mt-2 text-sm font-bold"
                  style={{ color: accentColor }}
                >
                  {member.role}
                </p>
              </article>
            ))}
          </div>
        ) : (
          <div className="mx-auto max-w-2xl rounded-[28px] border border-slate-200 bg-slate-50 p-10 text-center">
            <h2 className="text-2xl font-black">Our team</h2>

            <p className="mt-3 text-sm leading-7 text-slate-500">
              Team information will be published here as official company
              profiles are made available.
            </p>
          </div>
        )}

        <div className="mt-12 text-center">
          <Link
            href="/contact"
            className="inline-flex rounded-xl px-5 py-3 text-sm font-black text-white"
            style={{
              backgroundColor: primaryColor,
            }}
          >
            Contact Noble →
          </Link>
        </div>
      </section>
    </main>
  );
}
