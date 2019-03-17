package com.samourai.wallet.home.adapters;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.samourai.wallet.R;
import com.samourai.wallet.api.Tx;
import com.samourai.wallet.bip47.BIP47Meta;

import org.bitcoinj.core.Coin;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class TxAdapter extends RecyclerView.Adapter<TxAdapter.TxViewHolder> {

    private final int VIEW_ITEM = 1;
    private final int VIEW_SECTION = 0;
    private static final String TAG = "TxAdapter";
    private Context mContext;
    private List<Tx> txes;
    private CompositeDisposable disposables = new CompositeDisposable();
    private OnClickListener listener;

    public interface OnClickListener {
        void onClick(int position, Tx tx);
    }

    public TxAdapter(Context mContext, List<Tx> txes) {
        this.mContext = mContext;
        this.txes = new ArrayList<>();
        Disposable disposable = makeSectionedDataSet(txes).observeOn(Schedulers.computation())
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((List<Tx> list) -> {
                    this.txes = list;
                });
        disposables.add(disposable);

    }

    public void setClickListner(OnClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        disposables.dispose();
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @Override
    public long getItemId(int position) {
        return txes.get(position).getTS();
    }

    @Override
    public TxViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        if (viewType == VIEW_ITEM) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tx_item_layout_, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tx_item_section_layout, parent, false);
        }

        return new TxViewHolder(view, viewType);

    }

    @Override
    public void onBindViewHolder(TxViewHolder holder, int position) {

        Tx tx = txes.get(position);
        if (tx.section == null) {
            long _amount = 0L;
            Log.i(TAG, "onBindViewHolder: ".concat(String.valueOf(position)));
            if (tx.getAmount() < 0.0) {
                _amount = Math.abs((long) tx.getAmount());

            } else {
                _amount = (long) tx.getAmount();

            }
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            holder.tvDateView.setText(sdf.format(tx.getTS() * 1000L));
            if (tx.getConfirmations() <= 6) {
                holder.tvPendingStatus.setVisibility(View.VISIBLE);
            } else {
                holder.tvPendingStatus.setVisibility(View.INVISIBLE);
            }
            if (tx.getAmount() < 0.0) {
                holder.tvDirection.setImageDrawable(mContext.getDrawable(R.drawable.out_going_tx_whtie_arrow));
                holder.tvAmount.setTextColor(ContextCompat.getColor(mContext, R.color.white));
                holder.tvAmount.setText("-".concat(getBTCDisplayAmount(_amount).concat(" BTC")));

            } else {
                holder.tvDirection.setImageDrawable(mContext.getDrawable(R.drawable.incoming_tx_green));
                holder.tvAmount.setText(getBTCDisplayAmount(_amount).concat(" BTC"));
                holder.tvAmount.setTextColor(ContextCompat.getColor(mContext, R.color.green_ui_2));
            }
            if (tx.getPaymentCode() != null) {
                holder.tvPaynymId.setVisibility(View.VISIBLE);
                holder.tvPaynymId.setText(BIP47Meta.getInstance().getDisplayLabel(tx.getPaymentCode()));
            } else {
                holder.tvPaynymId.setVisibility(View.INVISIBLE);
            }
            if (this.listener != null)
                holder.itemView.setOnClickListener(view -> {
                    listener.onClick(position, tx);
                });
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("dd MMM YYY", Locale.ENGLISH);
            Date date = new Date(tx.getTS());
            if (DateUtils.isToday(tx.getTS())) {

                holder.tvSection.setText("Today");

            } else {
                holder.tvSection.setText(fmt.format(date));


            }

        }

    }

    @Override
    public int getItemCount() {
        return txes.size();
    }

    @Override
    public int getItemViewType(int position) {
        return txes.get(position).section != null ? VIEW_SECTION : VIEW_ITEM;
    }

    public void setTxes(List<Tx> txs) {
        Disposable disposable = makeSectionedDataSet(txs)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((List<Tx> list) -> {
                    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new TxDiffUtil(this.txes, list));
                    this.txes = list;
                    diffResult.dispatchUpdatesTo(this);
                });
        disposables.add(disposable);

    }

    public
    class TxViewHolder extends RecyclerView.ViewHolder {

        private TextView tvSection, tvDateView, tvAmount, tvPendingStatus, tvPaynymId;
        private ImageView tvDirection;


        public TxViewHolder(View itemView, int viewType) {
            super(itemView);
            if (viewType == VIEW_SECTION) {
                tvSection = itemView.findViewById(R.id.section_title);

            } else {

                tvDateView = itemView.findViewById(R.id.tx_time);

            }

            tvPendingStatus = itemView.findViewById(R.id.tx_pending_status);
            tvDirection = itemView.findViewById(R.id.TransactionDirection);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvPaynymId = itemView.findViewById(R.id.paynymId);

        }
    }


    private Observable<List<Tx>> makeSectionedDataSet(List<Tx> txes) {
        return Observable.fromCallable(() -> {
            Collections.sort(txes, (tx, t1) -> Long.compare(tx.getTS(), t1.getTS()));
            ArrayList<Long> sectionDates = new ArrayList<>();
            List<Tx> sectioned = new ArrayList<>();
            for (Tx tx : txes) {
                Date date = new Date();
                date.setTime(tx.getTS() * 1000);
                Calendar calendarDM = Calendar.getInstance();
                calendarDM.setTime(date);
                calendarDM.set(Calendar.HOUR, 0);
                calendarDM.set(Calendar.MINUTE, 0);
                calendarDM.set(Calendar.SECOND, 0);
                if (!sectionDates.contains(calendarDM.getTime().getTime())) {
                    sectionDates.add(calendarDM.getTime().getTime());
                }

            }

            Collections.sort(sectionDates, Long::compare);

            for (Long key : sectionDates) {
                Tx section = new Tx(new JSONObject());
                section.section = new Date(key).toString();
                section.setTS(key);
                for (Tx tx : txes) {
                    Date date = new Date();
                    date.setTime(tx.getTS() * 1000);
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
                    if (fmt.format(key).equals(fmt.format(date))) {
                        sectioned.add(tx);
                    }
                }
                sectioned.add(section);
            }
            Collections.reverse(sectioned);
            return sectioned;
        });

    }


    private String getBTCDisplayAmount(long value) {
        return Coin.valueOf(value).toPlainString();
    }

    private String getSatoshiDisplayAmount(long value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#", symbols);
        df.setMinimumIntegerDigits(1);
        df.setMaximumIntegerDigits(16);
        df.setGroupingUsed(true);
        df.setGroupingSize(3);
        return df.format(value);
    }


}
