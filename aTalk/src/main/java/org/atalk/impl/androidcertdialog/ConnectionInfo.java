/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import net.java.sip.communicator.impl.certificate.CertificateServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.plugin.jabberaccregwizz.JabberAccountRegistrationActivator;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.StringUtils;

import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings screen which displays protocolProvider connection info and servers SSL Certificates.
 * Allows user to revoke.
 *
 * @author Eng Chong Meng
 */
public class ConnectionInfo extends OSGiActivity
{
    /*
     * Adapter used to displays connection info and SSL certificates for all protocolProviders.
     */
    private ConnectionInfoAdapter mCIAdapter;

    /*
     * X509 SSL Certificate view on dialog window
     */
    private X509CertificateView viewCertDialog;

    private AlertDialog deleteDialog;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        ListView pProviderKeysList = findViewById(R.id.list);
        List<ProtocolProviderService> protocolProviders = new ArrayList<>(AccountUtils.getRegisteredProviders());

        this.mCIAdapter = new ConnectionInfoAdapter(protocolProviders);
        pProviderKeysList.setAdapter(mCIAdapter);

        pProviderKeysList.setOnItemClickListener((parent, view, position, id)
                -> showSslCertificate(position)
        );
    }

    /*
     * Dismissed any opened dialog to avoid window leaks on rotation
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        if (viewCertDialog != null && viewCertDialog.isShowing()) {
            viewCertDialog.dismiss();
            viewCertDialog = null;
        }
        if (deleteDialog != null && deleteDialog.isShowing()) {
            deleteDialog.dismiss();
            deleteDialog = null;
        }
    }

    /**
     * Displays alert asking user if he wants to delete the selected SSL Certificate.
     * Delete only the serviceName certificate but not the _xmpp-client.
     *
     * @param position the position of <tt>SSL Certificate</tt> in adapter's list which has to be used in the alert.
     */
    private void showSslCertificateDeleteAlert(int position)
    {
        ProtocolProviderService pps = (ProtocolProviderService) mCIAdapter.getItem(position);
        AccountID account = pps.getAccountID();
        final String bareJid = account.getAccountJid();
        final String certificateEntry = CertificateServiceImpl.PNAME_CERT_TRUST_PREFIX
                + CertificateServiceImpl.CERT_TRUST_PARAM_SUBFIX + account.getService();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.service_gui_settings_SSL_CERTIFICATE_DIALOG_TITLE)
                .setMessage(getString(R.string.service_gui_settings_SSL_CERTIFICATE_DELETE, bareJid))
                .setPositiveButton(R.string.service_gui_YES, (dialog, which) -> {
                    CertificateServiceImpl cvs
                            = (CertificateServiceImpl) JabberAccountRegistrationActivator.getCertificateService();
                    cvs.removeCertificateEntry(certificateEntry);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.service_gui_NO, (dialog, which) -> dialog.dismiss());
        deleteDialog = builder.create();
        deleteDialog.show();
    }

    /**
     * Displays SSL Certificate information.
     * Invoked when user clicks a link in the editor pane.
     *
     * @param position the position of <tt>SSL Certificate</tt> in adapter's list which will be displayed.
     */
    public void showSslCertificate(int position)
    {
        ProtocolProviderServiceJabberImpl pps = (ProtocolProviderServiceJabberImpl) mCIAdapter.getItem(position);
        if (pps.isRegistered()) {
            OperationSetTLS opSetTLS = pps.getOperationSet(OperationSetTLS.class);
            Certificate[] chain = opSetTLS.getServerCertificates();

            if (chain != null) {
                viewCertDialog = new X509CertificateView(this, chain);
                viewCertDialog.show();
            }
            else
                aTalkApp.showToastMessage(aTalkApp.getResString(R.string.service_gui_callinfo_TLS_CERTIFICATE_CONTENT) + ": null!");
        }
        else {
            aTalkApp.showToastMessage(R.string.service_gui_ACCOUNT_UNREGISTERED, pps.getOurJID());
        }
    }

    /**
     * Constructs the connection info text.
     */
    private String loadDetails(ProtocolProviderService pps)
    {
        final StringBuilder buff = new StringBuilder();
        buff.append("<html><body>");

        // Protocol name
        buff.append(getItemString(getString(R.string.service_gui_settings_PROTOCOL), pps.getProtocolName()));

        // Server address and port
        final OperationSetConnectionInfo opSetConnInfo = pps.getOperationSet(OperationSetConnectionInfo.class);
        if (opSetConnInfo != null) {
            InetSocketAddress ISAddress = opSetConnInfo.getServerAddress();
            buff.append(getItemString(getString(R.string.service_gui_settings_ADDRESS),
                    (ISAddress == null) ? "" : ISAddress.getHostName()));
            buff.append(getItemString(getString(R.string.service_gui_settings_PORT),
                    (ISAddress == null) ? "" : String.valueOf(ISAddress.getPort())));
        }

        // Transport protocol
        TransportProtocol preferredTransport = pps.getTransportProtocol();
        if (preferredTransport != TransportProtocol.UNKNOWN)
            buff.append(getItemString(getString(R.string.service_gui_callinfo_CALL_TRANSPORT), preferredTransport.toString()));

        // TLS information
        final OperationSetTLS opSetTLS = pps.getOperationSet(OperationSetTLS.class);
        if (opSetTLS != null) {
            buff.append(getItemString(getString(R.string.service_gui_callinfo_TLS_PROTOCOL), opSetTLS.getProtocol()));
            buff.append(getItemString(getString(R.string.service_gui_callinfo_TLS_CIPHER_SUITE), opSetTLS.getCipherSuite()));

            buff.append("<b><u><font color=\"aqua\">")
                    .append(getString(R.string.service_gui_callinfo_VIEW_CERTIFICATE))
                    .append("</font></u></b>");
        }
        buff.append("</body></html>");
        return buff.toString();
    }

    /**
     * Returns an HTML string corresponding to the given labelText and infoText,
     * that could be easily added to the information text pane.
     *
     * @param labelText the label text that would be shown in bold
     * @param infoText the info text that would be shown in plain text
     * @return the newly constructed HTML string
     */
    private String getItemString(String labelText, String infoText)
    {
        if (!StringUtils.isNullOrEmpty(infoText)) {
            if (infoText.contains("TLS"))
                infoText = "<small>" + infoText + "</small>";
        }
        else
            infoText = "";

        return "&#8226; <b>" + labelText + "</b> : " + infoText + "<br/>";
    }

    /**
     * Adapter which displays OTR private keys for given list of <tt>AccountID</tt>s.
     */
    class ConnectionInfoAdapter extends BaseAdapter
    {
        /**
         * List of <tt>protocolProviders</tt> for which the connection info and certificates are being displayed.
         */
        private final List<ProtocolProviderService> protocolProviders;

        /**
         * Creates new instance of <tt>SslCertificateListAdapter</tt>.
         *
         * @param pProviders the list of <tt>ProtocolProviderService</tt>s for which connection info and
         * certificates will be displayed by this adapter.
         */
        ConnectionInfoAdapter(List<ProtocolProviderService> pProviders)
        {
            protocolProviders = pProviders;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount()
        {
            return protocolProviders.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getItem(int position)
        {
            return protocolProviders.get(position);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getItemId(int position)
        {
            return position;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            // Keeps reference to avoid future findViewById()
            CIViewHolder ciViewHolder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.connection_info_list_row, parent, false);
                ciViewHolder = new CIViewHolder();
                ciViewHolder.pProtocolService = convertView.findViewById(R.id.protocolProvider);
                ciViewHolder.connectionInfo = convertView.findViewById(R.id.connectionInfo);
                convertView.setTag(ciViewHolder);
            }
            else {
                ciViewHolder = (CIViewHolder) convertView.getTag();
            }

            ProtocolProviderService pps = (ProtocolProviderService) getItem(position);
            String accountName = "<u>" + pps.getAccountID().getDisplayName() + "</u>";
            ciViewHolder.pProtocolService.setText(Html.fromHtml(accountName));

            String certificateInfo = loadDetails(pps);
            ciViewHolder.connectionInfo.setText(Html.fromHtml(certificateInfo));
            return convertView;
        }
    }

    private static class CIViewHolder
    {
        TextView pProtocolService;
        TextView connectionInfo;
    }
}
