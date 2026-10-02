export interface WebsiteSectionContent {
  title: string;
  body: string;
  bullets?: string[];
}

export interface WebsitePageContent {
  visible: boolean;
  eyebrow: string;
  title: string;
  description: string;
  primaryHref?: string;
  primaryLabel?: string;
  secondaryHref?: string;
  secondaryLabel?: string;
  sections: WebsiteSectionContent[];
}

export interface WebsiteContent {
  version: number;
  pages: Record<string, WebsitePageContent>;
}

const page = (
  eyebrow: string,
  title: string,
  description: string,
  sections: WebsiteSectionContent[],
  options: Partial<
    Omit<WebsitePageContent, "eyebrow" | "title" | "description" | "sections">
  > = {},
): WebsitePageContent => ({
  visible: true,
  eyebrow,
  title,
  description,
  sections,
  ...options,
});

const hiddenLegacyPage = (title: string) =>
  page(
    "Noble Loan Solutions",
    title,
    "This legacy information page is not part of the current public lending catalogue.",
    [
      {
        title: "Current lending catalogue",
        body: "Please use Noble's current loan products and published application journey for Rwanda.",
      },
      {
        title: "Need help choosing",
        body: "Contact Noble for current product information and eligibility guidance.",
      },
    ],
    { visible: false },
  );

export const DEFAULT_WEBSITE_CONTENT: WebsiteContent = {
  version: 1,
  pages: {
    home: page(
      "Trusted lending for Rwanda",
      "Real support. Bigger dreams.",
      "Flexible lending for personal needs, business growth, vehicles, salary advances and agriculture—with clear terms and support at every step.",
      [
        {
          title: "A loan for your next step.",
          body: "Explore active Noble loan solutions and review the applicable terms before you apply.",
        },
        {
          title: "Know what comes next.",
          body: "Choose a product, plan the numbers, submit your application and keep your reference for secure tracking.",
        },
        {
          title: "Clarity belongs at every step.",
          body: "Review costs, understand repayment and ask questions whenever something is unclear.",
        },
      ],
      {
        primaryHref: "/services",
        primaryLabel: "Explore our loans",
        secondaryHref: "/how-it-works",
        secondaryLabel: "How it works",
      },
    ),
    services: page(
      "Loan solutions",
      "Financing designed around real needs.",
      "Review Noble's active lending products, compare the applicable charges and choose the route that fits your borrowing need.",
      [
        {
          title: "Personal borrowing",
          body: "Structured financing for approved individual and household needs.",
        },
        {
          title: "Business finance",
          body: "Working-capital and business-growth financing for eligible businesses.",
        },
        {
          title: "Asset-focused finance",
          body: "Vehicle and agriculture financing where the active product supports the need.",
        },
      ],
      {
        primaryHref: "/apply",
        primaryLabel: "Start an application",
        secondaryHref: "/calculators",
        secondaryLabel: "Plan the numbers",
      },
    ),
    calculators: page(
      "Financial tools",
      "Plan the numbers before you make the decision.",
      "Use Noble's calculators for planning. Actual product pricing and repayment obligations are governed by the applicable loan agreement and the terms shown during your application.",
      [
        {
          title: "Loan calculator",
          body: "Model an indicative repayment schedule using an active Noble product.",
        },
        {
          title: "Payment calculator",
          body: "Start from a payment target and estimate the financing amount that may fit within it.",
        },
        {
          title: "Interest calculator",
          body: "Separate indicative interest and management charges from the overall repayment.",
        },
        {
          title: "Inflation calculator",
          body: "Understand how an assumed annual inflation rate changes purchasing power over time.",
        },
      ],
    ),
    "loan-calculator": page(
      "Financial planning",
      "Loan calculator",
      "Estimate an indicative Noble repayment using the currently configured product pricing and term.",
      [
        {
          title: "Use an active product",
          body: "Select an active Noble product so the estimate reflects the configuration currently published by the organisation.",
        },
        {
          title: "Review the whole cost",
          body: "Consider the principal, interest, management charges and any other applicable fees before committing.",
        },
      ],
    ),
    "payment-calculator": page(
      "Payment planning",
      "Payment calculator",
      "Start with a payment level you are comfortable considering and explore an indicative borrowing scenario.",
      [
        {
          title: "Set a payment target",
          body: "Enter the amount you would like the first repayment to be around, then test different terms.",
        },
        {
          title: "Compare scenarios",
          body: "Changing the term changes the indicative amount supported by the same payment target.",
        },
        {
          title: "Keep it illustrative",
          body: "Approval, pricing, fees and contractual repayments are determined through the application and loan agreement.",
        },
      ],
    ),
    "interest-calculator": page(
      "Financial planning",
      "Interest calculator",
      "Understand how configured interest and management charges affect the overall cost of an indicative Noble loan.",
      [
        {
          title: "See interest separately",
          body: "Model the estimated interest component over a selected term.",
        },
        {
          title: "See the full cost",
          body: "Review management charges and total scheduled repayment alongside the interest estimate.",
        },
      ],
    ),
    "inflation-calculator": page(
      "Financial tools",
      "Inflation calculator",
      "Estimate the future amount needed to match today's purchasing power using an assumed annual inflation rate.",
      [
        {
          title: "Choose an assumption",
          body: "Use an annual inflation assumption and a time horizon to model how purchasing power changes.",
        },
        {
          title: "Use for planning",
          body: "Inflation varies over time. The result is an illustration, not a forecast or guarantee.",
        },
      ],
    ),
    "how-it-works": page(
      "How Noble lending works",
      "A clear path from loan choice to application tracking.",
      "Noble keeps the borrowing journey simple: understand the loan, plan the repayment, apply securely and keep your application reference.",
      [
        {
          title: "Choose a loan",
          body: "Review Noble's available lending products and select the option that matches your borrowing need.",
        },
        {
          title: "Plan the repayment",
          body: "Use the calculator to see an indicative repayment scenario based on the applicable product configuration.",
        },
        {
          title: "Submit your application",
          body: "Provide the information and supporting documents requested through Noble's secure digital application journey.",
        },
        {
          title: "Keep your reference",
          body: "After submission, retain your application reference and phone details so you can securely check progress.",
        },
      ],
      {
        primaryHref: "/apply",
        primaryLabel: "Start an application",
        secondaryHref: "/track",
        secondaryLabel: "Track an application",
      },
    ),
    about: page(
      "About Noble",
      "A professional lending partner built around trust and transparency.",
      "Learn about Noble Loan Solutions, its purpose, the market it serves and the principles behind its public lending experience.",
      [
        {
          title: "Our purpose",
          body: "Provide clear, practical financial support while keeping lending terms understandable.",
        },
        {
          title: "Our market",
          body: "Noble's public lending experience is designed around clients and borrowing needs in Rwanda.",
        },
        {
          title: "Our standards",
          body: "Responsible lending, secure information handling, clear communication and respectful customer service.",
        },
      ],
    ),
    "our-story": page(
      "Our story",
      "A lending business built around trust.",
      "Learn how Noble approaches lending, customer service and responsible financial support in Rwanda.",
      [
        {
          title: "Our mission",
          body: "Provide honest, fairly-priced credit to eligible clients while keeping the customer journey clear and respectful.",
        },
        {
          title: "Our vision",
          body: "Build long-term trust through transparent lending and financial dignity.",
        },
        {
          title: "What we stand for",
          body: "Clarity, responsibility, privacy, service quality and continuous improvement.",
        },
      ],
    ),
    leadership: page(
      "Leadership",
      "The people responsible for the Noble experience.",
      "Meet the verified leadership team configured for public publication by Noble Loan Solutions.",
      [
        {
          title: "Verified information",
          body: "Only leadership profiles approved for public publication should appear on this page.",
        },
        {
          title: "Accountability",
          body: "Strong governance supports responsible lending, service quality and protection of customer information.",
        },
      ],
    ),
    careers: page(
      "Careers",
      "Build a better lending experience with Noble.",
      "We welcome people who care about responsible finance, secure technology, excellent service and practical solutions for clients.",
      [
        {
          title: "Meaningful work",
          body: "Lending affects real households and businesses. Good product, operations, risk and technology work can make the customer journey clearer and safer.",
        },
        {
          title: "What we value",
          body: "Integrity, accountability, careful execution, customer empathy and continuous improvement.",
        },
        {
          title: "Open opportunities",
          body: "Only verified vacancies should be published. Contact Noble for current opportunities.",
        },
      ],
      { primaryHref: "/contact", primaryLabel: "Ask about opportunities" },
    ),
    affiliates: page(
      "Partners",
      "Work with Noble responsibly.",
      "Explore partnership opportunities that improve access, service and customer support while respecting privacy and applicable requirements.",
      [
        {
          title: "Partner standards",
          body: "Partners should operate transparently, protect customer information and follow applicable laws, contracts and Noble requirements.",
        },
        {
          title: "Customer protection",
          body: "A referral or partner relationship does not change the applicable loan terms, eligibility requirements or approval process.",
        },
        {
          title: "Become a partner",
          body: "Businesses interested in a verified partnership can contact Noble with their organisation details and proposed service.",
        },
      ],
      { primaryHref: "/contact", primaryLabel: "Contact partnerships" },
    ),
    learn: page(
      "Financial education",
      "Better borrowing starts with better information.",
      "Explore practical guidance about loan planning, repayment, responsible borrowing, applications and customer privacy.",
      [
        {
          title: "Plan before you borrow",
          body: "Use Noble's calculators and product pages to understand the amount, term and indicative cost before applying.",
        },
        {
          title: "Protect your information",
          body: "Use official Noble channels and never share passwords, OTP codes or confidential credentials through public forms.",
        },
        {
          title: "Ask questions",
          body: "Good lending decisions depend on understanding the terms. Contact Noble when something needs clarification.",
        },
      ],
    ),
    faq: page(
      "Loan FAQs",
      "Straight answers about borrowing with Noble.",
      "Find practical information about products, applications, repayment planning and application tracking.",
      [
        {
          title: "What loan products does Noble offer?",
          body: "The public site currently presents Personal, Business, Vehicle, Salary Advance and Agriculture lending products. Availability and terms depend on active product configuration.",
        },
        {
          title: "How much can I borrow?",
          body: "The minimum amount and any configured maximum are shown on the relevant product. Final approved amounts remain subject to assessment.",
        },
        {
          title: "Can I track my application?",
          body: "Yes. Keep your application reference and phone number, then use Track application to check the status available for you.",
        },
        {
          title: "Does using the calculator guarantee approval?",
          body: "No. Calculators are planning tools. Eligibility, approval, fees and final repayments are determined through assessment and the applicable agreement.",
        },
      ],
    ),
    help: page(
      "Borrower help",
      "Need help with a Noble loan or application?",
      "Use the right support channel for your question and keep your application reference available when contacting the Noble team.",
      [
        {
          title: "New application",
          body: "Start from Apply when you are ready to provide the information and supporting documents requested.",
        },
        {
          title: "Existing application",
          body: "Use Track application with your reference and phone number to view the status available to you.",
        },
        {
          title: "Repayment questions",
          body: "For questions about an existing loan or repayment, contact Noble through the published support channels.",
        },
        {
          title: "Stay safe",
          body: "Never share passwords, OTP codes, card PINs or other confidential credentials through the public contact form.",
        },
      ],
      {
        primaryHref: "/contact",
        primaryLabel: "Contact Noble",
        secondaryHref: "/track",
        secondaryLabel: "Track application",
      },
    ),
    news: page(
      "News & updates",
      "Updates from Noble Loan Solutions.",
      "This page is reserved for verified company announcements, service updates and public notices.",
      [
        {
          title: "Company updates",
          body: "Confirmed announcements may include product changes, service improvements, regulatory notices and customer-facing operational updates.",
        },
        {
          title: "Public notices",
          body: "Important notices affecting applications, payments or customer service should be published here when applicable.",
        },
        {
          title: "Stay informed",
          body: "For questions about an existing application, use Track application or contact the Noble team directly.",
        },
      ],
    ),
    "borrower-stories": page(
      "Borrower stories",
      "Real experiences, only when verified.",
      "Verified customer stories can be displayed here when Noble has approved them for public use.",
      [
        {
          title: "Authentic experiences",
          body: "Noble should only publish stories and testimonials that are genuine, approved and suitable for public use.",
        },
        {
          title: "No promises",
          body: "Customer experiences do not guarantee that another applicant will receive the same outcome.",
        },
      ],
    ),
    contact: page(
      "Noble customer support",
      "Talk to the team behind your loan journey.",
      "Ask a question about a loan, application, repayment or eligibility. Your message is routed into the Noble dashboard so the lending team can review and respond.",
      [
        {
          title: "Start an enquiry",
          body: "Use the secure contact form to send a question to the Noble team. Include enough detail for the right staff member to respond.",
        },
        {
          title: "Existing application",
          body: "Keep your application reference and phone number ready when asking about a submitted application.",
        },
        {
          title: "Protect your information",
          body: "Do not submit passwords, OTP codes, card PINs or other confidential credentials through the public contact form.",
        },
      ],
      {
        primaryHref: "/apply",
        primaryLabel: "Start an application",
        secondaryHref: "/track",
        secondaryLabel: "Track an application",
      },
    ),
    track: page(
      "Secure loan lookup",
      "Track your application with confidence.",
      "Use your application reference and the phone number used during the application to securely view available status, repayment, document and communication information.",
      [
        {
          title: "Keep your reference",
          body: "Your reference and phone number are used together to retrieve the public status available for your application.",
        },
        {
          title: "Stay informed",
          body: "Check milestones, document requirements, messages and repayment information from one place.",
        },
      ],
    ),
    apply: page(
      "Start your application",
      "A clearer way to apply for lending.",
      "Choose the loan you need, review the terms and provide the information required for Noble's assessment.",
      [
        {
          title: "Choose the right product",
          body: "Review the active product catalogue before selecting the loan type for your application.",
        },
        {
          title: "Prepare your information",
          body: "Have the identity, contact, income, business or supporting documents that the application requests available.",
        },
        {
          title: "Track after submission",
          body: "Keep your application reference and phone number so you can securely check progress later.",
        },
      ],
    ),
    privacy: page(
      "Privacy",
      "Your information deserves careful protection.",
      "Read how Noble Loan Solutions approaches personal information used in lending, customer support, security and compliance.",
      [
        {
          title: "Information and purpose",
          body: "Noble may process identity, contact, financial, employment and supporting information for legitimate lending, service, security and legal purposes.",
        },
        {
          title: "Your choices",
          body: "Where applicable, you may have rights relating to access, correction and other handling of personal information. Contact Noble using the published details.",
        },
        {
          title: "Security",
          body: "Noble should maintain appropriate administrative, technical and organisational safeguards for information under its control.",
        },
      ],
    ),
    "privacy-choices": page(
      "Privacy choices",
      "Understand your information choices.",
      "Learn about the choices and requests that may be available in relation to personal information handled by Noble.",
      [
        {
          title: "Your choices",
          body: "Depending on the information and applicable law, you may have rights relating to access, correction, deletion, restriction or other handling.",
        },
        {
          title: "Marketing preferences",
          body: "Optional communications can be adjusted through the published Noble support channels. Required service or regulatory communications may still be sent.",
        },
        {
          title: "Make a request",
          body: "Use the official contact details and provide enough information for the team to verify and respond securely.",
        },
      ],
    ),
    "data-collection": page(
      "Data collection",
      "What information may be collected during lending.",
      "A lending application can require identity, contact, employment, income, financial and supporting-document information for assessment and servicing.",
      [
        {
          title: "Application information",
          body: "Information may include identity details, contact information, requested loan information, income and employment information and verification documents.",
        },
        {
          title: "Why it is collected",
          body: "Information can be used to verify identity, assess eligibility and affordability, process applications, service loans, communicate with clients, prevent fraud and meet legal obligations.",
        },
        {
          title: "Security and retention",
          body: "Information should be protected with appropriate access controls and security measures and retained only as required for legitimate operational, contractual or legal purposes.",
        },
      ],
    ),
    terms: page(
      "Terms",
      "Understand the terms before you use Noble services.",
      "Review the terms governing the public website, application journey, lending information and use of Noble's digital services.",
      [
        {
          title: "Website information",
          body: "Public website content is provided for general information and planning. Product pricing and contractual terms shown during an application take precedence for a specific transaction.",
        },
        {
          title: "Applications",
          body: "An application is subject to verification, eligibility, affordability and other applicable assessment requirements. Submission does not itself create an approval.",
        },
        {
          title: "Responsible use",
          body: "Use Noble services honestly and protect your credentials and personal information.",
        },
      ],
    ),
    accessibility: page(
      "Accessibility",
      "A website designed to be usable by more people.",
      "Noble is committed to improving the accessibility of its public digital experience and making important lending information easier to understand and navigate.",
      [
        {
          title: "Accessible information",
          body: "Noble aims to use clear headings, readable text, keyboard-friendly controls, meaningful links and appropriate labels.",
        },
        {
          title: "Need assistance?",
          body: "If you have difficulty accessing information or completing a digital step, contact Noble for an alternative path.",
        },
        {
          title: "Continuous improvement",
          body: "Accessibility is an ongoing process. Feedback can help identify improvements as the public experience evolves.",
        },
      ],
    ),
    "state-licenses": page(
      "Regulatory information",
      "Public company and licensing information.",
      "Use this page to review verified company registration, regulatory and licensing information published by Noble.",
      [
        {
          title: "Verify before publishing",
          body: "Only current, verified registration and licence information should be published on the public website.",
        },
        {
          title: "Questions",
          body: "Contact Noble when you need clarification about a registration or regulatory statement.",
        },
      ],
    ),
    "personal-loans": page(
      "Personal lending",
      "Personal Loan",
      "Personal financing for approved individual and household needs, subject to Noble's assessment and applicable terms.",
      [
        {
          title: "Personal needs",
          body: "Structured financing for eligible individual and household needs.",
        },
        {
          title: "Plan your repayment",
          body: "Use Noble's calculators to explore an indicative repayment scenario before applying.",
        },
        {
          title: "Apply with confidence",
          body: "Review the exact fees, dates and obligations shown in the application and agreement before accepting any offer.",
        },
      ],
    ),
    "business-loans": page(
      "Business finance",
      "Business Finance",
      "Financing for eligible business working-capital and growth needs, subject to Noble's assessment and active product configuration.",
      [
        {
          title: "Business activity",
          body: "For eligible working-capital and growth needs.",
        },
        {
          title: "Review the numbers",
          body: "Compare the requested amount, fees, term and total repayment before committing.",
        },
        {
          title: "Prepare your documents",
          body: "Business applications may require registration, financial and supporting documentation for verification.",
        },
      ],
    ),
    "vehicle-loans": page(
      "Vehicle finance",
      "Vehicle Finance",
      "Financing for approved vehicle purchases, subject to the active product configuration and assessment.",
      [
        {
          title: "Vehicle purchase",
          body: "For an approved vehicle financing need.",
        },
        {
          title: "Understand the amount",
          body: "Review the current product range and applicable fees before making a purchase decision.",
        },
        {
          title: "Plan the repayment",
          body: "Use a calculator to understand an indicative schedule before applying.",
        },
      ],
    ),
    "salary-advance": page(
      "Salary advance",
      "Salary Advance Loan",
      "Short-term financing against verified salary income, subject to eligibility, affordability and Noble's active product terms.",
      [
        {
          title: "Verified salary income",
          body: "Designed for eligible short-term borrowing needs supported by verified salary income.",
        },
        {
          title: "Keep the term in mind",
          body: "Review the repayment timing and total cost before accepting a salary advance.",
        },
        {
          title: "Protect affordability",
          body: "Borrow only an amount that can reasonably be repaid from the expected income available.",
        },
      ],
    ),
    "agriculture-loans": page(
      "Agriculture finance",
      "Agriculture Loan",
      "Financing for approved agricultural and agribusiness activities within Noble's active lending policy.",
      [
        {
          title: "Agriculture & agribusiness",
          body: "For approved agricultural and agribusiness activities.",
        },
        {
          title: "Plan around the cycle",
          body: "Consider the purpose, timing and repayment plan before committing to finance.",
        },
        {
          title: "Prepare supporting information",
          body: "Agriculture applications may require supporting information relevant to the activity and assessment.",
        },
      ],
    ),
    "credit-score": hiddenLegacyPage("Legacy financial education page"),
    "credit-card-consolidation": hiddenLegacyPage(
      "Legacy personal lending page",
    ),
    "debt-consolidation": hiddenLegacyPage("Legacy personal lending page"),
    "home-improvement-loans": hiddenLegacyPage("Legacy personal lending page"),
    "medical-loans": hiddenLegacyPage("Legacy personal lending page"),
    "moving-loans": hiddenLegacyPage("Legacy personal lending page"),
    "short-term-relief": hiddenLegacyPage("Legacy personal lending page"),
    "wedding-loans": hiddenLegacyPage("Legacy personal lending page"),
  },
};

export function mergeWebsiteContent(
  stored?: Partial<WebsiteContent> | null,
): WebsiteContent {
  const incomingPages =
    stored?.pages && typeof stored.pages === "object" ? stored.pages : {};
  const mergedPages: Record<string, WebsitePageContent> = {};

  for (const [key, fallback] of Object.entries(DEFAULT_WEBSITE_CONTENT.pages)) {
    const incoming = incomingPages[key];
    if (!incoming || typeof incoming !== "object") {
      mergedPages[key] = fallback;
      continue;
    }

    const sections = Array.isArray(incoming.sections)
      ? incoming.sections
          .filter((item) => item && typeof item === "object")
          .map((item) => ({
            title: String(item.title ?? "").trim(),
            body: String(item.body ?? "").trim(),
            bullets: Array.isArray(item.bullets)
              ? item.bullets
                  .map((bullet) => String(bullet).trim())
                  .filter(Boolean)
              : undefined,
          }))
          .filter((item) => item.title || item.body)
      : fallback.sections;

    mergedPages[key] = {
      ...fallback,
      ...incoming,
      visible: incoming.visible !== false,
      eyebrow: String(incoming.eyebrow ?? fallback.eyebrow),
      title: String(incoming.title ?? fallback.title),
      description: String(incoming.description ?? fallback.description),
      primaryHref: String(incoming.primaryHref ?? fallback.primaryHref ?? ""),
      primaryLabel: String(
        incoming.primaryLabel ?? fallback.primaryLabel ?? "",
      ),
      secondaryHref: String(
        incoming.secondaryHref ?? fallback.secondaryHref ?? "",
      ),
      secondaryLabel: String(
        incoming.secondaryLabel ?? fallback.secondaryLabel ?? "",
      ),
      sections,
    };
  }

  for (const [key, incoming] of Object.entries(incomingPages)) {
    if (mergedPages[key] || !incoming || typeof incoming !== "object") continue;
    mergedPages[key] = {
      visible: incoming.visible !== false,
      eyebrow: String(incoming.eyebrow ?? "Noble Loan Solutions"),
      title: String(incoming.title ?? key),
      description: String(incoming.description ?? ""),
      primaryHref: String(incoming.primaryHref ?? ""),
      primaryLabel: String(incoming.primaryLabel ?? ""),
      secondaryHref: String(incoming.secondaryHref ?? ""),
      secondaryLabel: String(incoming.secondaryLabel ?? ""),
      sections: Array.isArray(incoming.sections)
        ? incoming.sections.map((item) => ({
            title: String(item?.title ?? ""),
            body: String(item?.body ?? ""),
            bullets: Array.isArray(item?.bullets)
              ? item.bullets.map(String)
              : undefined,
          }))
        : [],
    };
  }

  return {
    version: Number(stored?.version ?? DEFAULT_WEBSITE_CONTENT.version),
    pages: mergedPages,
  };
}

export function pageContent(
  content: WebsiteContent | undefined,
  key: string,
): WebsitePageContent {
  return (
    content?.pages?.[key] ??
    DEFAULT_WEBSITE_CONTENT.pages[key] ?? {
      visible: true,
      eyebrow: "Noble Loan Solutions",
      title: key,
      description: "",
      sections: [],
    }
  );
}
