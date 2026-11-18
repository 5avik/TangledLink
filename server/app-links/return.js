// Never read or trust invoiceId/status from the browser URL.
history.replaceState(null, '', '/billing/return');
if (/android/i.test(navigator.userAgent)) {
  window.location.replace('intent://billing/return#Intent;scheme=tangledlink;package=pro.tngl;end');
}
