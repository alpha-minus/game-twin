package com.twinlore.app.data.privacy

/**
 * Static JavaScript stub injected as the first child of <head> on Quantcast-using pages.
 *
 * Defines window.__tcfapi (TCF v2) and window.__gpp answering "no consent, GDPR doesn't apply,
 * cmp loaded" so the page's own scripts proceed past their consent gate without Quantcast's
 * loader running. Idempotent: if window.__tcfapi already exists, the stub is a no-op so a
 * second injection on the same page is harmless.
 */
internal object ConsentStubs {

    val STUB_SCRIPT_TAG: String = """
<script>(function () {
  try {
    if (window.__tcfapi) return;
    var nextListenerId = 0;
    var tcData = {
      tcString: '',
      tcfPolicyVersion: 2,
      cmpId: 0,
      cmpVersion: 0,
      gdprApplies: false,
      eventStatus: 'tcloaded',
      cmpStatus: 'loaded',
      listenerId: 0,
      purpose: { consents: {}, legitimateInterests: {} },
      vendor: { consents: {}, legitimateInterests: {} },
      specialFeatureOptins: {}
    };
    window.__tcfapi = function (command, version, callback, parameter) {
      try {
        if (typeof callback !== 'function') return;
        if (command === 'ping') {
          callback({
            gdprApplies: false,
            cmpLoaded: true,
            cmpStatus: 'loaded',
            displayStatus: 'disabled',
            apiVersion: '2.2',
            cmpId: 0,
            cmpVersion: 0,
            gvlVersion: 0,
            tcfPolicyVersion: 2
          }, true);
          return;
        }
        if (command === 'addEventListener') {
          var data = Object.assign({}, tcData, { listenerId: nextListenerId++ });
          callback(data, true);
          return;
        }
        if (command === 'removeEventListener') {
          callback(true, true);
          return;
        }
        if (command === 'getTCData' || command === 'getInAppTCData') {
          callback(tcData, true);
          return;
        }
        if (command === 'getVendorList') {
          callback({ vendorListVersion: 0, vendors: {} }, true);
          return;
        }
        callback(null, false);
      } catch (e) { /* swallow */ }
    };
  } catch (e) { /* swallow */ }
  try {
    if (window.__gpp) return;
    window.__gpp = function (command, callback, parameter) {
      try {
        if (typeof callback !== 'function') return;
        if (command === 'ping') {
          callback({
            signalStatus: 'ready',
            gppString: '',
            applicableSections: [-1],
            supportedAPIs: [],
            cmpStatus: 'loaded',
            cmpDisplayStatus: 'disabled'
          }, true);
          return;
        }
        callback({ signalStatus: 'ready', gppString: '', applicableSections: [-1] }, true);
      } catch (e) { /* swallow */ }
    };
  } catch (e) { /* swallow */ }
})();</script>
    """.trimIndent()
}
